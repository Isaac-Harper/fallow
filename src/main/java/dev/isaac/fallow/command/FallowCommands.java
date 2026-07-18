package dev.isaac.fallow.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.api.FallowSeasons;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.diet.DietData;
import dev.isaac.fallow.diet.DietGroup;
import dev.isaac.fallow.diet.DietWindow;
import dev.isaac.fallow.ecology.EcologyScheduler;
import dev.isaac.fallow.season.SeasonService;
import dev.isaac.fallow.season.SeasonState;
import dev.isaac.fallow.trail.TrailData;
import dev.isaac.fallow.trail.TrailSystem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code /fallow season} - query the season; {@code set} is op-only (for admins and testing).
 * {@code /fallow stats} - ecology scheduler timings. {@code /fallow reload} - re-read the JSON
 * config on a live server (op-only).
 */
public final class FallowCommands {
    private FallowCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("fallow")
                .then(Commands.literal("season")
                    .executes(FallowCommands::querySeason)
                    .then(Commands.literal("set")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("season", StringArgumentType.word())
                            .suggests((c, b) -> {
                                for (Season s : Season.values()) {
                                    b.suggest(s.id());
                                }
                                return b.buildFuture();
                            })
                            .executes(c -> setSeason(c, 0))
                            .then(Commands.argument("day", IntegerArgumentType.integer(0))
                                .executes(c -> setSeason(c, IntegerArgumentType.getInteger(c, "day")))))))
                .then(Commands.literal("stats").executes(FallowCommands::stats))
                .then(Commands.literal("trails")
                    .then(Commands.literal("reset")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(FallowCommands::resetTrails)))
                .then(Commands.literal("reload")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(FallowCommands::reload))
                .then(Commands.literal("diet")
                    .executes(FallowCommands::queryDiet))));
    }

    private static int querySeason(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        FallowSeasons.SeasonInfo info = FallowSeasons.get(source.getServer());
        if (!FallowSeasons.enabled()) {
            source.sendSuccess(() -> Component.literal(
                "Seasons are disabled (frozen at " + info.season().id() + ")"), false);
            return 0;
        }
        double mult = FallowSeasons.growthMultiplier(source.getServer());
        double portion = Fallow.CONFIG.dayNight.enabled
            ? Fallow.CONFIG.dayNight.dayPortion(info.season())
            : 0.5;
        source.sendSuccess(() -> Component.literal(String.format(
            "Season: %s (day %d of %d) - growth x%.2f, daylight %.0f%%",
            info.season().id(), info.dayInSeason() + 1, info.daysPerSeason(), mult, portion * 100)), false);
        return 1;
    }

    private static int setSeason(CommandContext<CommandSourceStack> c, int day) {
        CommandSourceStack source = c.getSource();
        String id = StringArgumentType.getString(c, "season");
        Season season = Season.byId(id);
        if (season == null) {
            source.sendFailure(Component.literal("Unknown season '" + id + "' (spring, summer, autumn, winter)"));
            return 0;
        }
        int clampedDay = Math.min(day, Fallow.CONFIG.seasons.daysPerSeason - 1);
        SeasonState.get(source.getServer()).set(season, clampedDay);
        SeasonService.invalidate();
        source.sendSuccess(() -> Component.literal(
            "Season set to " + season.id() + ", day " + (clampedDay + 1)), true);
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        for (String line : EcologyScheduler.statsLines()) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        source.sendSuccess(() -> Component.literal(TrailSystem.statsLine()), false);
        return 1;
    }

    private static int resetTrails(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        TrailData data = TrailData.get(source.getLevel());
        int cleared = data.wear.size();
        data.wear.clear();
        data.setDirty();
        source.sendSuccess(() -> Component.literal(
            "Cleared " + cleared + " trail wear entries in this dimension"), true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        Fallow.reload(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.literal("Fallow config reloaded"), true);
        return 1;
    }

    private static int queryDiet(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        ServerPlayer player = source.getPlayerOrException();
        DietData data = DietData.get(source.getServer());
        DietWindow window = data.windowFor(player.getUUID());

        Set<String> covered = window.distinctGroups();
        Set<String> allGroups = Arrays.stream(DietGroup.values())
            .map(DietGroup::id)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> missing = new LinkedHashSet<>(allGroups);
        missing.removeAll(covered);

        int score = covered.size();
        int tier;
        if (score >= 6) {
            tier = 2;
        } else if (score >= Fallow.CONFIG.diet.tierOneGroups) {
            tier = 1;
        } else {
            tier = 0;
        }

        String coveredStr = covered.isEmpty() ? "-" : String.join(", ", covered);
        String missingStr = missing.isEmpty() ? "-" : String.join(", ", missing);
        Component tierLabel = tier == 2
            ? Component.translatable("fallow.diet.tier.two")
            : tier == 1
                ? Component.translatable("fallow.diet.tier.one")
                : Component.translatable("fallow.diet.tier.none");

        source.sendSuccess(() -> Component.translatable("fallow.diet.status.header",
            String.valueOf(score), String.valueOf(6)), false);
        source.sendSuccess(() -> Component.translatable("fallow.diet.status.covered",
            Component.literal(coveredStr)), false);
        source.sendSuccess(() -> Component.translatable("fallow.diet.status.missing",
            Component.literal(missingStr)), false);
        source.sendSuccess(() -> Component.translatable("fallow.diet.status.tier", tierLabel), false);
        source.sendSuccess(() -> Component.translatable("fallow.diet.status.meals",
            String.valueOf(window.mealCount())), false);
        return 1;
    }
}
