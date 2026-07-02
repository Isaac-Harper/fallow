package dev.isaac.fallow;

import dev.isaac.fallow.command.FallowCommands;
import dev.isaac.fallow.ecology.BambooSpreadTask;
import dev.isaac.fallow.ecology.DiebackTask;
import dev.isaac.fallow.ecology.EcologyScheduler;
import dev.isaac.fallow.ecology.FlowerWiltTask;
import dev.isaac.fallow.ecology.FruitDropTask;
import dev.isaac.fallow.ecology.LeafLitterTask;
import dev.isaac.fallow.ecology.OvercrowdingTask;
import dev.isaac.fallow.ecology.PrecipitationTask;
import dev.isaac.fallow.ecology.SaplingSpreadTask;
import dev.isaac.fallow.ecology.ShorelineCreepTask;
import dev.isaac.fallow.ecology.VegetationSproutTask;
import dev.isaac.fallow.growth.BiomeGrowthRates;
import dev.isaac.fallow.growth.BiomeTuning;
import dev.isaac.fallow.growth.ConfigGrowthRates;
import dev.isaac.fallow.growth.GrowthRateProvider;
import dev.isaac.fallow.growth.SeasonalGrowthRates;
import dev.isaac.fallow.item.FallowItems;
import dev.isaac.fallow.network.SeasonSyncPayload;
import dev.isaac.fallow.notice.FirstJoinNotice;
import dev.isaac.fallow.season.PrecipitationBiomes;
import dev.isaac.fallow.season.SeasonEventService;
import dev.isaac.fallow.season.SeasonService;
import dev.isaac.fallow.season.WeatherService;
import dev.isaac.fallow.trail.TrailSystem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint. Simulation is server-side, driven by Fabric lifecycle events. Three mixins:
 * two client-side cosmetic ({@code BiomeMixin} seasonal foliage tint, {@code LeafFallMixin}
 * seasonal falling-leaf particles) and one common gameplay ({@code BiomeTemperatureMixin}, the
 * seasonal-temperature precipitation lever - the deliberate exception to the otherwise
 * events-and-public-APIs rule). The client source set also adds the Mod Menu config screen.
 */
public class Fallow implements ModInitializer {
    public static final String MOD_ID = "fallow";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Live config. Replaced wholesale by {@code /fallow reload}; read it fresh, never cache fields. */
    public static volatile FallowConfig CONFIG = new FallowConfig();

    /**
     * The single source of growth probabilities. Call sites never read chances from the config
     * directly. Layered outermost-last: config base -> seasonal scaling -> per-biome growth
     * scaling. Each layer is a pass-through when its feature is neutral/disabled.
     */
    public static final GrowthRateProvider GROWTH_RATES =
        new BiomeGrowthRates(new SeasonalGrowthRates(new ConfigGrowthRates()));

    /**
     * The single config-update path: re-read the file, swap {@link #CONFIG} wholesale, and
     * re-derive everything cached from it. Both {@code /fallow reload} and the config screen's
     * Done button land here; anything a config change must refresh belongs in this method, not
     * at its call sites. {@code server} may be null (config screen outside a world).
     */
    public static void reload(MinecraftServer server) {
        CONFIG = FallowConfig.load();
        SeasonService.invalidate();
        if (server != null) {
            BiomeTuning.rebuild(server.registryAccess()); // re-resolve per-biome tuning
        }
    }

    @Override
    public void onInitialize() {
        CONFIG = FallowConfig.load();

        PayloadTypeRegistry.clientboundPlay().register(SeasonSyncPayload.ID, SeasonSyncPayload.CODEC);

        FallowItems.register(); // the season_clock item + its creative-tab entry

        SeasonEventService.register(); // before SeasonService: sets the heatwave temp bonus first
        SeasonService.register();
        WeatherService.register();
        PrecipitationBiomes.register();
        EcologyScheduler.register();
        EcologyScheduler.registerTask(new VegetationSproutTask(GROWTH_RATES));
        EcologyScheduler.registerTask(new DiebackTask(GROWTH_RATES));
        EcologyScheduler.registerTask(new SaplingSpreadTask(GROWTH_RATES));
        EcologyScheduler.registerTask(new LeafLitterTask(GROWTH_RATES));
        EcologyScheduler.registerTask(new ShorelineCreepTask(GROWTH_RATES));
        EcologyScheduler.registerTask(new BambooSpreadTask(GROWTH_RATES));
        EcologyScheduler.registerTask(new OvercrowdingTask(GROWTH_RATES));
        EcologyScheduler.registerTask(new FlowerWiltTask(GROWTH_RATES));
        EcologyScheduler.registerTask(new PrecipitationTask());
        EcologyScheduler.registerTask(new FruitDropTask(GROWTH_RATES));
        TrailSystem.register();
        FallowCommands.register();
        FirstJoinNotice.register(); // one-time "this mod changes blocks" notice, shown even when disabled

        LOGGER.info("Fallow initialized");
    }
}
