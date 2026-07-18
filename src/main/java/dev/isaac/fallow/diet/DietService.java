package dev.isaac.fallow.diet;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.season.SeasonClock;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Drives the diet mechanic on the server: records meals (via {@link #recordMeal}) and applies
 * Absorption when the player's rolling window covers enough distinct groups. Ticks every 20
 * server ticks (once per second) to keep effects refreshed without per-frame overhead.
 */
public final class DietService {
    private static int tickCounter = 0;

    private DietService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DietService::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        FallowConfig cfg = Fallow.CONFIG;
        if (!cfg.enabled || !cfg.diet.enabled) {
            return;
        }
        tickCounter++;
        if (tickCounter < 20) {
            return;
        }
        tickCounter = 0;

        DietData data = DietData.get(server);
        long currentDay = SeasonClock.day();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DietWindow window = data.windowFor(player.getUUID());

            if (cfg.diet.mealExpiryDays != 0) {
                window.prune(currentDay, cfg.diet.mealExpiryDays);
                data.setDirty();
            }

            applyEffects(player, window, cfg.diet);
        }
    }

    /**
     * Applies (or withholds) Absorption based on the player's current diet score. Public so
     * gametests can call it directly with a known window state without going through the
     * 20-tick poll cycle.
     */
    public static void applyEffects(ServerPlayer player, DietWindow window, FallowConfig.Diet cfg) {
        int score = window.distinctGroups().size();
        if (score >= 6) {
            // Tier 2: all 6 groups covered
            player.addEffect(new MobEffectInstance(
                MobEffects.ABSORPTION, 20 * 35, cfg.tierTwoAmplifier, true, false, true));
        } else if (score >= cfg.tierOneGroups) {
            // Tier 1: threshold met but not all 6
            player.addEffect(new MobEffectInstance(
                MobEffects.ABSORPTION, 20 * 35, cfg.tierOneAmplifier, true, false, true));
        }
        // Below tier 1: let existing absorption decay naturally
    }

    /**
     * Records one eating event for the player. Called from {@link dev.isaac.fallow.mixin.LivingEntityEatMixin}
     * when a player finishes consuming an item.
     */
    public static void recordMeal(ServerPlayer player, ItemStack eaten) {
        FallowConfig cfg = Fallow.CONFIG;
        if (!cfg.enabled || !cfg.diet.enabled) {
            return;
        }

        Set<DietGroup> matched = DietGroup.groupsOf(eaten, player.level());
        if (matched.isEmpty()) {
            return;
        }

        Set<String> groupIds = matched.stream()
            .map(DietGroup::id)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        DietData data = DietData.get(player.level().getServer());
        DietWindow window = data.windowFor(player.getUUID());

        Set<String> previousGroups = window.distinctGroups();
        window.push(groupIds, SeasonClock.day(), cfg.diet.windowSize);
        data.setDirty();

        if (cfg.diet.announceNewGroups) {
            Set<String> added = window.newGroups(previousGroups);
            for (String groupName : added) {
                player.sendOverlayMessage(
                    Component.translatable("fallow.diet.group_added", groupName));
            }
        }
    }
}
