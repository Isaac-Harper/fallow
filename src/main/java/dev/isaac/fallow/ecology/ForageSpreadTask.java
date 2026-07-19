package dev.isaac.fallow.ecology;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.growth.GrowthChannel;
import dev.isaac.fallow.growth.GrowthRateProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Places wild forage plants ({@code fallow:wild_onion}, {@code fallow:strawberry_bush}) in
 * biome-appropriate locations. Mirrors {@link VegetationSproutTask}'s column-sampling idiom:
 * random column, heightmap, grass-block ground, light check, density guard.
 *
 * <p>Biome eligibility is checked via {@link #biomeMatches}: each homes entry may list exact
 * biome ids or biome tags ("{@code #tag}"); a candidate is eligible if the biome at the column
 * matches at least one entry for that plant.
 *
 * <p>The FORAGE growth channel follows the shared seasonal curve (not PER_SPECIES-exempt), so
 * wild spread naturally slows in winter along with the rest of the ecosystem.
 */
public final class ForageSpreadTask implements EcologyTask {
    private static final int DENSITY_RADIUS = 8;
    private static final int DENSITY_MAX = 2; // skip if 2+ of same type in radius

    private final GrowthRateProvider rates;

    public ForageSpreadTask(GrowthRateProvider rates) {
        this.rates = rates;
    }

    @Override
    public String id() {
        return "fallow:forage_spread";
    }

    @Override
    public boolean enabled(FallowConfig config) {
        return config.enabled && config.crops.enabled && config.crops.wild.enabled;
    }

    @Override
    public int visitChunk(ServerLevel level, LevelChunk chunk, RandomSource random, int samples) {
        FallowConfig.Crops.Wild cfg = Fallow.CONFIG.crops.wild;
        if (cfg.homes == null || cfg.homes.isEmpty()) {
            return 0;
        }
        ChunkPos chunkPos = chunk.getPos();
        int placed = 0;
        for (int i = 0; i < samples; i++) {
            int x = chunkPos.getMinBlockX() + random.nextInt(16);
            int z = chunkPos.getMinBlockZ() + random.nextInt(16);
            // Surface column: first air block above the topmost motion-blocking surface.
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinY() || y > level.getMaxY()) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            // Must be air with grass block below.
            if (!level.getBlockState(pos).isAir()
                || !level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) {
                continue;
            }
            // Light gate (same threshold as VegetationSproutTask).
            if (level.getMaxLocalRawBrightness(pos) < Fallow.CONFIG.vegetation.minLightLevel) {
                continue;
            }
            // Chance roll through the provider stack (shared seasonal curve + biome growth).
            if (random.nextFloat() >= rates.chance(GrowthChannel.FORAGE, level, pos)) {
                continue;
            }
            // Find eligible plants for this biome (resolve the biome holder once).
            Holder<Biome> biome = level.getBiome(pos);
            String biomeId = biome.unwrapKey()
                .map(k -> k.identifier().toString()).orElse("");
            Set<String> biomeTags = new HashSet<>();
            biome.tags().forEach(t -> biomeTags.add("#" + t.location().toString()));

            List<String> eligible = new ArrayList<>();
            for (var entry : cfg.homes.entrySet()) {
                if (isEligible(biomeId, biomeTags, entry.getValue())) {
                    eligible.add(entry.getKey());
                }
            }
            if (eligible.isEmpty()) {
                continue;
            }
            String chosen = eligible.get(random.nextInt(eligible.size()));
            // Density guard: skip if 2+ blocks of the same type within radius 8.
            Block block = resolveBlock(chosen);
            if (block == null) {
                continue;
            }
            if (tooManyNearby(level, pos, block)) {
                continue;
            }
            BlockState toPlace = block.defaultBlockState();
            if (toPlace.canSurvive(level, pos)) {
                level.setBlock(pos, toPlace, Block.UPDATE_ALL);
                placed++;
            }
        }
        return placed;
    }

    /**
     * True if the biome at the candidate column matches at least one entry in the homes list.
     * Entries starting with '#' are treated as biome tags; others are exact ids.
     * Pure logic; unit-testable.
     */
    public static boolean isEligible(String biomeId, Set<String> biomeTags,
            List<String> homeEntries) {
        if (homeEntries == null) {
            return false;
        }
        for (String entry : homeEntries) {
            if (entry.startsWith("#")) {
                if (biomeTags.contains(entry)) {
                    return true;
                }
            } else if (entry.equals(biomeId)) {
                return true;
            }
        }
        return false;
    }

    /** True when >= {@link #DENSITY_MAX} blocks of the same type exist within the density radius. */
    private static boolean tooManyNearby(ServerLevel level, BlockPos pos, Block block) {
        int count = 0;
        for (BlockPos p : BlockPos.betweenClosed(
                pos.offset(-DENSITY_RADIUS, -2, -DENSITY_RADIUS),
                pos.offset(DENSITY_RADIUS, 2, DENSITY_RADIUS))) {
            if (level.getBlockState(p).is(block) && ++count >= DENSITY_MAX) {
                return true;
            }
        }
        return false;
    }

    private static Block resolveBlock(String blockId) {
        int colon = blockId.indexOf(':');
        Identifier id = colon < 0
            ? Identifier.fromNamespaceAndPath("minecraft", blockId)
            : Identifier.fromNamespaceAndPath(blockId.substring(0, colon),
                blockId.substring(colon + 1));
        Optional<Block> opt = BuiltInRegistries.BLOCK.getOptional(id);
        return opt.orElse(null);
    }
}
