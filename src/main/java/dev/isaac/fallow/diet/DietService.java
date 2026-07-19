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
 *
 * <p>Absorption is refreshed conservatively: vanilla {@code AbsorptionMobEffect.onEffectStarted}
 * runs unconditionally on every {@code addEffect} and resets absorption hearts back to the tier
 * maximum, so blindly re-adding the effect each second would regenerate hearts consumed in combat.
 * Instead the grant is only re-applied when the current instance is absent, weaker, or close to
 * expiring (see {@link #shouldReapply}); hearts consumed in combat therefore stay consumed until
 * the effect naturally cycles.
 */
public final class DietService {
    private static int tickCounter = 0;

    /** Ticks of the 35s grant remaining below which a same-tier refresh is allowed. */
    private static final int REFRESH_WINDOW_TICKS = 20 * 5;

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

            if (cfg.diet.mealExpiryDays != 0 && window.prune(currentDay, cfg.diet.mealExpiryDays)) {
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
        int amplifier;
        if (score >= 6) {
            amplifier = cfg.tierTwoAmplifier; // Tier 2: all 6 groups covered
        } else if (score >= cfg.tierOneGroups) {
            amplifier = cfg.tierOneAmplifier; // Tier 1: threshold met but not all 6
        } else {
            return; // Below tier 1: let existing absorption decay naturally
        }

        // Only re-add when the grant needs it. Re-adding resets absorption hearts to the tier
        // maximum (vanilla onEffectStarted), so an unconditional refresh would regenerate hearts
        // spent in combat every second.
        MobEffectInstance existing = player.getEffect(MobEffects.ABSORPTION);
        int existingDuration = existing == null ? 0 : existing.getDuration();
        int existingAmplifier = existing == null ? -1 : existing.getAmplifier();
        if (!shouldReapply(existing != null, existingDuration, existingAmplifier, amplifier)) {
            return;
        }
        player.addEffect(new MobEffectInstance(
            MobEffects.ABSORPTION, 20 * 35, amplifier, true, false, true));
    }

    /**
     * Pure decision for whether the Absorption grant should be re-added this tick. True when there
     * is no current instance, the current instance is a weaker tier (needs upgrading), or it is
     * within {@link #REFRESH_WINDOW_TICKS} of expiring. A same-or-stronger instance with time left
     * is left untouched so combat-consumed hearts are not regenerated.
     */
    static boolean shouldReapply(boolean present, int existingDurationTicks,
            int existingAmplifier, int desiredAmplifier) {
        if (!present) {
            return true;
        }
        if (existingAmplifier < desiredAmplifier) {
            return true;
        }
        return existingDurationTicks <= REFRESH_WINDOW_TICKS;
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
