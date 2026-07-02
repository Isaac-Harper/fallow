package dev.isaac.fallow.ecology;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.growth.GrowthChannel;
import dev.isaac.fallow.growth.GrowthRateProvider;
import dev.isaac.fallow.season.SeasonClock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;

/**
 * Seasonal fruiting: in its configured season, a natural tree occasionally drops a fruit item on
 * the open ground beneath its canopy (oaks -> apples in autumn by default). The tree is identified
 * by a non-persistent leaf block overhead - player-placed leaves are always persistent, so log
 * cabins and leaf builds never fruit, consistent with the sapling/leaf-litter heuristic.
 */
public final class FruitDropTask implements EcologyTask {
    private final GrowthRateProvider rates;

    public FruitDropTask(GrowthRateProvider rates) {
        this.rates = rates;
    }

    @Override
    public String id() {
        return "fruiting";
    }

    @Override
    public boolean enabled(FallowConfig config) {
        return config.fruiting.enabled;
    }

    @Override
    public int visitChunk(ServerLevel level, LevelChunk chunk, RandomSource random, int samples) {
        FallowConfig.Fruiting cfg = Fallow.CONFIG.fruiting;
        if (cfg.types == null || cfg.types.isEmpty()) {
            return 0;
        }
        Season season = SeasonClock.season();
        ChunkPos chunkPos = chunk.getPos();
        int dropped = 0;
        for (int i = 0; i < samples; i++) {
            int x = chunkPos.getMinBlockX() + random.nextInt(16);
            int z = chunkPos.getMinBlockZ() + random.nextInt(16);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinY() || y > level.getMaxY()) {
                continue;
            }
            BlockPos spot = new BlockPos(x, y, z);
            if (!level.getBlockState(spot).isAir()) {
                continue; // need an open spot under the canopy to drop into
            }
            FallowConfig.Fruiting.FruitType type = canopyFruit(level, spot, cfg, season, random);
            if (type == null) {
                continue;
            }
            ItemStack stack = stackFor(type);
            if (stack.isEmpty()) {
                continue;
            }
            ItemEntity entity = new ItemEntity(level, x + 0.5, y + 0.5, z + 0.5, stack);
            entity.setDeltaMovement(0.0, -0.1, 0.0);
            if (level.addFreshEntity(entity)) {
                dropped++;
            }
        }
        return dropped;
    }

    /** Look up for a natural fruiting canopy; return its entry if in season and the roll passes. */
    private FallowConfig.Fruiting.FruitType canopyFruit(ServerLevel level, BlockPos spot,
            FallowConfig.Fruiting cfg, Season season, RandomSource random) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(spot.getX(), spot.getY(), spot.getZ());
        int maxY = level.getMaxY();
        for (int i = 0; i < cfg.scanHeight && cursor.getY() < maxY; i++) {
            cursor.move(0, 1, 0);
            BlockState state = level.getBlockState(cursor);
            if (!state.is(BlockTags.LEAVES)
                || !state.hasProperty(BlockStateProperties.PERSISTENT)
                || state.getValue(BlockStateProperties.PERSISTENT)) {
                continue;
            }
            FallowConfig.Fruiting.FruitType type = cfg.types.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            if (type == null) {
                continue;
            }
            // Per-type season gate (an unknown season string is nulled with a warning at config
            // load). With seasons disabled the clock is frozen, so every type is in season.
            if (Fallow.CONFIG.seasons.enabled) {
                Season fruitSeason = Season.byId(type.season);
                if (fruitSeason != null && fruitSeason != season) {
                    return null;
                }
            }
            // Per-type base chance x the provider stack's scaling (biome growth, heatwave stall;
            // the FRUIT channel is curve-exempt because the season gate above is the curve).
            return random.nextFloat() < type.chance * rates.chance(GrowthChannel.FRUIT, level, spot)
                ? type : null;
        }
        return null;
    }

    private static ItemStack stackFor(FallowConfig.Fruiting.FruitType type) {
        Optional<? extends Item> item = BuiltInRegistries.ITEM.getOptional(id(type.item));
        return item.isEmpty() ? ItemStack.EMPTY : new ItemStack(item.get());
    }

    private static Identifier id(String s) {
        int colon = s.indexOf(':');
        return colon < 0
            ? Identifier.fromNamespaceAndPath("minecraft", s)
            : Identifier.fromNamespaceAndPath(s.substring(0, colon), s.substring(colon + 1));
    }
}
