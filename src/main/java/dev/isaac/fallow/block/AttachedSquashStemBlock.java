package dev.isaac.fallow.block;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.Fallow;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;

/**
 * Attached squash stem, the mature form that has grown a squash. Reuses vanilla
 * {@link AttachedStemBlock} unchanged (stem = {@code fallow:squash_stem}, fruit =
 * {@code fallow:squash}, seed = {@code fallow:squash_seeds}) and rides pumpkin's support tag. Only
 * the season-growing stem needs a subclass override; the attached form is inert, so this is a thin
 * wrapper to reach the protected vanilla constructor.
 */
public final class AttachedSquashStemBlock extends AttachedStemBlock {
    // AttachedStemBlock.codec() returns MapCodec<AttachedStemBlock>; the cast is safe because this
    // CODEC only ever constructs AttachedSquashStemBlock instances.
    @SuppressWarnings("unchecked")
    public static final MapCodec<AttachedStemBlock> CODEC =
        (MapCodec<AttachedStemBlock>) (MapCodec<?>) simpleCodec(AttachedSquashStemBlock::new);

    private static final ResourceKey<Block> STEM =
        blockKey("squash_stem");
    private static final ResourceKey<Block> FRUIT =
        blockKey("squash");
    private static final ResourceKey<Item> SEED =
        itemKey("squash_seeds");

    public AttachedSquashStemBlock(Properties properties) {
        super(STEM, FRUIT, SEED, BlockTags.SUPPORTS_PUMPKIN_STEM, properties);
    }

    @Override
    public MapCodec<AttachedStemBlock> codec() {
        return CODEC;
    }

    private static ResourceKey<Block> blockKey(String path) {
        return ResourceKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Fallow.MOD_ID, path));
    }

    private static ResourceKey<Item> itemKey(String path) {
        return ResourceKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(Fallow.MOD_ID, path));
    }
}
