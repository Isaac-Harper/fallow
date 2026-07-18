package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.season.SeasonClock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Squash stem, an Idiom-E gourd crop mirroring vanilla pumpkin. Reuses vanilla {@link StemBlock}
 * for the whole grow/fruit lifecycle (fruit = {@code fallow:squash}, attached stem =
 * {@code fallow:attached_squash_stem}, seed = {@code fallow:squash_seeds}), and rides vanilla's
 * pumpkin support tags so no new block tags are needed.
 *
 * <p>The one addition over vanilla is the season gate: {@link #randomTick} applies the shared crop
 * season weight (key {@code fallow:squash_stem}) before delegating to vanilla growth, and at WINTER
 * with weight &lt;=0 and winterKill on the stem becomes a dead husk.
 */
public final class SquashStemBlock extends StemBlock {
    // StemBlock.codec() returns MapCodec<StemBlock>; the cast is safe because this CODEC only ever
    // constructs SquashStemBlock instances.
    @SuppressWarnings("unchecked")
    public static final MapCodec<StemBlock> CODEC =
        (MapCodec<StemBlock>) (MapCodec<?>) simpleCodec(SquashStemBlock::new);

    private static final ResourceKey<Block> FRUIT =
        blockKey("squash");
    private static final ResourceKey<Block> ATTACHED_STEM =
        blockKey("attached_squash_stem");
    private static final ResourceKey<Item> SEED =
        itemKey("squash_seeds");

    public SquashStemBlock(Properties properties) {
        // Reuse pumpkin's support tags: squash grows on farmland and fruits onto the same ground.
        super(FRUIT, ATTACHED_STEM, SEED,
            BlockTags.SUPPORTS_PUMPKIN_STEM, BlockTags.SUPPORTS_PUMPKIN_STEM_FRUIT, properties);
    }

    @Override
    public MapCodec<StemBlock> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random) {
        if (shouldGateGrowth()) {
            double w = Fallow.CONFIG.crops.cropSeasonWeight("fallow:squash_stem",
                SeasonClock.season());
            if (SeasonClock.season() == Season.WINTER && w <= 0.0 && Fallow.CONFIG.crops.winterKill) {
                // Winter-kill: the stem becomes a dead husk.
                level.setBlock(pos, FallowBlocks.WITHERED_CROP.defaultBlockState(), Block.UPDATE_ALL);
                return;
            }
            if (w <= 0.0) {
                return;
            }
            if (random.nextFloat() >= w) {
                return;
            }
        }
        super.randomTick(state, level, pos, random);
    }

    private static ResourceKey<Block> blockKey(String path) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Fallow.MOD_ID, path));
    }

    private static ResourceKey<Item> itemKey(String path) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,
            Identifier.fromNamespaceAndPath(Fallow.MOD_ID, path));
    }

    private static boolean shouldGateGrowth() {
        return Fallow.CONFIG.enabled
            && Fallow.CONFIG.crops.enabled
            && Fallow.CONFIG.crops.seasonGating
            && Fallow.CONFIG.seasons.enabled;
    }
}
