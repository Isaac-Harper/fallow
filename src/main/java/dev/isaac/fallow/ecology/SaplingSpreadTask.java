package dev.isaac.fallow.ecology;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.biome.BiomeTuning;
import dev.isaac.fallow.growth.GrowthChannel;
import dev.isaac.fallow.growth.GrowthRateProvider;
import dev.isaac.fallow.season.SeasonClock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;

/**
 * Phase 3 task: mature trees occasionally seed a matching sapling on nearby valid ground, so
 * forests creep outward over time.
 *
 * <p>Worldgen-datapack-safe by design (Arboria/Tectonic etc.): we never read biome tree
 * features. Instead we detect the logs that actually grew nearby and map log type -> sapling
 * by registry name ({@link SaplingNames}), copying whatever stands there regardless of which
 * datapack generated it.
 *
 * <p>Tree-vs-player-build heuristic (the open problem Gaia's Breath doesn't solve - any log
 * within 5 blocks triggers it, so log cabins sprout saplings): a log only counts as a tree
 * anchor when (a) its vertical column bottoms out on dirt-family ground, and (b) the canopy
 * region above the column top contains leaves with {@code persistent=false} - player-placed
 * leaves are always persistent, so natural canopy is a cheap single-property signal. Cabins,
 * fences, and lumber piles fail both. All of this runs only after the (rare) probability
 * roll passes.
 */
public final class SaplingSpreadTask implements EcologyTask {
    private static final int LOG_ANCHOR_ATTEMPTS = 4;

    private final GrowthRateProvider rates;

    public SaplingSpreadTask(GrowthRateProvider rates) {
        this.rates = rates;
    }

    @Override
    public String id() {
        return "sapling_spread";
    }

    @Override
    public boolean enabled(FallowConfig config) {
        return config.saplings.enabled;
    }

    @Override
    public int visitChunk(ServerLevel level, LevelChunk chunk, RandomSource random, int samples) {
        FallowConfig.Saplings cfg = Fallow.CONFIG.saplings;
        ChunkPos chunkPos = chunk.getPos();
        int placed = 0;
        for (int i = 0; i < samples; i++) {
            int x = chunkPos.getMinBlockX() + random.nextInt(16);
            int z = chunkPos.getMinBlockZ() + random.nextInt(16);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinY() || y > level.getMaxY()) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            // SUBSTRATE_OVERWORLD = #dirt + #grass_blocks + #mud + #moss: the 26.1 "dirt-family
            // ground" tag (the plain DIRT tag no longer contains grass blocks or podzol).
            if (!level.getBlockState(pos).isAir()
                || !level.getBlockState(pos.below()).is(BlockTags.SUBSTRATE_OVERWORLD)
                || level.getMaxLocalRawBrightness(pos) < Fallow.CONFIG.vegetation.minLightLevel) {
                continue;
            }
            if (random.nextFloat() >= rates.chance(GrowthChannel.SAPLING, level, pos)) {
                continue;
            }
            placed += trySeedSapling(level, pos, random, cfg);
        }
        return placed;
    }

    private static int trySeedSapling(ServerLevel level, BlockPos pos, RandomSource random, FallowConfig.Saplings cfg) {
        // Identify the parent tree (and thus the species) first, so the density cap can be
        // this species' own - dense canopies (jungle, dark oak) pack tight, savanna acacia stays
        // sparse - rather than one global cap for everything.
        Anchor anchor = findVerifiedTreeLog(level, pos, cfg);
        if (anchor == null) {
            return 0;
        }
        Block sapling = saplingFor(anchor.log());
        if (sapling == null) {
            return 0;
        }
        FallowConfig.Saplings.TreeType type = typeFor(sapling, cfg);
        // Mega-only species (dark/pale oak) never grow as a lone sapling: four must share a flat 2x2,
        // which vanilla then grows on random tick. Nudge this seed up to clusterRadius blocks onto
        // the cell that best advances a nearby partial 2x2 (a lone founder otherwise). Everything
        // below runs against the chosen cell so the per-species gates apply where it actually lands.
        BlockPos target = pos;
        if (type != null && type.twoByTwo && cfg.clusterRadius > 0) {
            target = clusterTarget(level, pos, sapling, cfg);
            if (target == null) {
                return 0;
            }
        }
        int baseDensity = type != null ? type.density : cfg.maxSaplingsNearby;
        // Per-species cap, scaled per biome (lush x1.5, arid x0.2). Same value used as both the
        // counter limit and threshold - counting only to a smaller number disables the guard.
        int densityCap = BiomeTuning.resolveCap(baseDensity, BiomeTuning.densityMultiplier(level, target));
        if (densityCap == 0 || countNearbySaplings(level, target, cfg, densityCap) >= densityCap) {
            return 0;
        }
        // Per-species spread radius: a sapling never seeds beyond its parent's dispersal distance.
        if (type != null) {
            int dx = Math.abs(anchor.pos().getX() - target.getX());
            int dz = Math.abs(anchor.pos().getZ() - target.getZ());
            if (Math.max(dx, dz) > type.radius) {
                return 0;
            }
        }
        // Prolificacy x per-species seasonal timing. Unlike every other growth channel, sapling
        // seasonality is per-species (saplings.types[].phenology) - white oak peaks in autumn,
        // spruce keeps a winter trickle, mangrove never shuts down, jungle is aseasonal - so it
        // is applied here rather than through the shared SeasonalGrowthRates curve. Unlisted
        // (modded) types fall back to that shared curve at full prolificacy.
        double rate = type != null ? type.rate : 1.0;
        if (Fallow.CONFIG.seasons.enabled) {
            // Phase-shifted per biome (e.g. savanna's acacia follows its summer wet season),
            // consistent with the vegetation growth/overcrowding seasons; from the per-tick cache.
            Season season = SeasonClock.season().shifted(BiomeTuning.seasonPhase(level, target));
            rate *= type != null
                ? type.seasonWeight(season, Fallow.CONFIG.seasons)
                : Fallow.CONFIG.seasons.multiplier(season);
        }
        if (random.nextFloat() >= rate) {
            return 0;
        }
        BlockState state = sapling.defaultBlockState();
        if (!state.canSurvive(level, target)) {
            return 0;
        }
        level.setBlock(target, state, Block.UPDATE_ALL);
        return 1;
    }

    /**
     * Maps the pure {@link SaplingCluster} targeting onto the world for a mega-only species: a cell
     * is "occupied" when a same-species sapling already stands there at the candidate's ground level
     * (coplanar - a 2x2 only grows on flat ground), and "placeable" when it is empty, lit, and over
     * dirt-family substrate the sapling can survive on. Returns the chosen position, or null when
     * nothing within {@code clusterRadius} is plantable this event.
     */
    private static BlockPos clusterTarget(ServerLevel level, BlockPos pos, Block sapling, FallowConfig.Saplings cfg) {
        int y = pos.getY();
        BlockState saplingState = sapling.defaultBlockState();
        int minLight = Fallow.CONFIG.vegetation.minLightLevel;
        BlockPos.MutableBlockPos occ = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        SaplingCluster.CellTest occupied = (x, z) -> level.getBlockState(occ.set(x, y, z)).is(sapling);
        SaplingCluster.CellTest placeable = (x, z) ->
            level.getBlockState(cell.set(x, y, z)).isAir()
                && level.getBlockState(cell.set(x, y - 1, z)).is(BlockTags.SUBSTRATE_OVERWORLD)
                && level.getMaxLocalRawBrightness(cell.set(x, y, z)) >= minLight
                && saplingState.canSurvive(level, cell.set(x, y, z));
        int[] chosen = SaplingCluster.chooseTarget(pos.getX(), pos.getZ(), cfg.clusterRadius, occupied, placeable);
        return chosen == null ? null : new BlockPos(chosen[0], y, chosen[1]);
    }

    private static FallowConfig.Saplings.TreeType typeFor(Block sapling, FallowConfig.Saplings cfg) {
        if (cfg.types == null) {
            return null;
        }
        return cfg.types.get(BuiltInRegistries.BLOCK.getKey(sapling).toString());
    }

    /** A validated tree anchor: which log block, and where it was found. */
    private record Anchor(Block log, BlockPos pos) {
    }

    /**
     * Scan for logs near the candidate; validate up to a few distinct anchors as real trees.
     * The scan is a thin y-band around candidate height (rooted trunks start near the same
     * terrain level), which bounds the no-logs worst case to ~2k state reads - and this runs
     * only after the rare probability roll already passed.
     */
    private static Anchor findVerifiedTreeLog(ServerLevel level, BlockPos center, FallowConfig.Saplings cfg) {
        int r = cfg.logSearchRadius;
        int attempts = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 4, r))) {
            BlockState state = level.getBlockState(p);
            if (!state.is(BlockTags.LOGS)) {
                continue;
            }
            if (isNaturalTree(level, p, cfg)) {
                return new Anchor(state.getBlock(), p.immutable());
            }
            if (++attempts >= LOG_ANCHOR_ATTEMPTS) {
                return null;
            }
        }
        return null;
    }

    /**
     * (a) Walk the vertical log column: its base must sit on dirt-family ground.
     * (b) The canopy box above the column top must contain non-persistent leaves.
     */
    private static boolean isNaturalTree(ServerLevel level, BlockPos logPos, FallowConfig.Saplings cfg) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(logPos.getX(), logPos.getY(), logPos.getZ());
        // Descend to the column base.
        int steps = 0;
        while (steps++ < cfg.maxColumnHeight && level.getBlockState(cursor.below()).is(BlockTags.LOGS)) {
            cursor.move(0, -1, 0);
        }
        if (!level.getBlockState(cursor.below()).is(BlockTags.SUBSTRATE_OVERWORLD)) {
            return false;
        }
        // Ascend to the column top.
        steps = 0;
        while (steps++ < cfg.maxColumnHeight && level.getBlockState(cursor.above()).is(BlockTags.LOGS)) {
            cursor.move(0, 1, 0);
        }
        // Canopy check around the top (covers bent trunks like acacia within the box).
        BlockPos top = cursor.immutable();
        for (BlockPos p : BlockPos.betweenClosed(top.offset(-3, 0, -3), top.offset(3, 3, 3))) {
            BlockState state = level.getBlockState(p);
            if (state.is(BlockTags.LEAVES)
                && state.hasProperty(BlockStateProperties.PERSISTENT)
                && !state.getValue(BlockStateProperties.PERSISTENT)) {
                return true;
            }
        }
        return false;
    }

    /** Counts saplings within the density box, early-exiting at {@code cap} (the resolved cap). */
    private static int countNearbySaplings(ServerLevel level, BlockPos pos, FallowConfig.Saplings cfg, int cap) {
        int r = cfg.densityRadius;
        int count = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -2, -r), pos.offset(r, 2, r))) {
            if (level.getBlockState(p).is(BlockTags.SAPLINGS)) {
                if (++count >= cap) {
                    return count;
                }
            }
        }
        return count;
    }

    private static Block saplingFor(Block log) {
        Identifier logId = BuiltInRegistries.BLOCK.getKey(log);
        for (String path : SaplingNames.saplingCandidates(logId.getPath())) {
            Optional<? extends Block> block = BuiltInRegistries.BLOCK
                .getOptional(Identifier.fromNamespaceAndPath(logId.getNamespace(), path));
            if (block.isPresent() && block.get() != Blocks.AIR) {
                return block.get();
            }
        }
        return null;
    }
}
