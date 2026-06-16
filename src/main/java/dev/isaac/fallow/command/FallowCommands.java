package dev.isaac.fallow.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.api.FallowSeasons;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.ecology.EcologyScheduler;
import dev.isaac.fallow.growth.BiomeTuning;
import dev.isaac.fallow.season.SeasonService;
import dev.isaac.fallow.season.SeasonState;
import dev.isaac.fallow.trail.TrailData;
import dev.isaac.fallow.trail.TrailSystem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

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
                    .executes(FallowCommands::reload))));
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
        Fallow.CONFIG = FallowConfig.load();
        SeasonService.invalidate();
        BiomeTuning.rebuild(c.getSource().getServer().registryAccess()); // re-resolve per-biome tuning
        c.getSource().sendSuccess(() -> Component.literal("Fallow config reloaded"), true);
        return 1;
    }
}
