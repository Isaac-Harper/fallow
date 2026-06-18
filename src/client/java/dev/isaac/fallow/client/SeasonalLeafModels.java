package dev.isaac.fallow.client;

import dev.isaac.fallow.api.Season;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Seasonal leaf recoloring via a model swap. Tinting can only darken the (grey) vanilla leaf
 * textures, so it can't produce the brighter autumn hues - a yellow tint reads as olive, and
 * cherry's pink texture has no tint slot at all. Instead each (block, season) here bakes a
 * standalone untinted model carrying a recolored texture, and we swap it in for that block while
 * its season is active. Off-season the vanilla model (and its subtle {@link SeasonTint} tint) is
 * used, so other seasons stay vanilla. The swap is picked up on the chunk re-mesh that
 * {@link FallowClientSeasons} already triggers on season change, and works for both the vanilla
 * renderer and Sodium (both resolve the model through {@code BlockStateModelSet.get}).
 */
public final class SeasonalLeafModels {
    private record Variant(Block block, Season season, Identifier modelId,
                           ExtraModelKey<BlockStateModel> key) {
    }

    private static Variant variant(Block block, Season season, String path) {
        Identifier id = Identifier.fromNamespaceAndPath("fallow", path);
        return new Variant(block, season, id, ExtraModelKey.create(id::toString));
    }

    private static final List<Variant> VARIANTS = List.of(
        variant(Blocks.BIRCH_LEAVES, Season.AUTUMN, "block/birch_leaves_autumn"),
        variant(Blocks.BIRCH_LEAVES, Season.WINTER, "block/birch_leaves_winter"),
        variant(Blocks.OAK_LEAVES, Season.AUTUMN, "block/oak_leaves_autumn"),
        variant(Blocks.DARK_OAK_LEAVES, Season.AUTUMN, "block/dark_oak_leaves_autumn"),
        variant(Blocks.CHERRY_LEAVES, Season.AUTUMN, "block/cherry_leaves_autumn"),
        variant(Blocks.CHERRY_LEAVES, Season.WINTER, "block/cherry_leaves_winter"));

    /** block -> (season -> baked-model key); built once from {@link #VARIANTS}. */
    private static final Map<Block, Map<Season, ExtraModelKey<BlockStateModel>>> BY_BLOCK = buildIndex();

    private SeasonalLeafModels() {
    }

    private static Map<Block, Map<Season, ExtraModelKey<BlockStateModel>>> buildIndex() {
        Map<Block, Map<Season, ExtraModelKey<BlockStateModel>>> index = new HashMap<>();
        for (Variant v : VARIANTS) {
            index.computeIfAbsent(v.block(), b -> new EnumMap<>(Season.class)).put(v.season(), v.key());
        }
        return index;
    }

    public static void register() {
        ModelLoadingPlugin.register(ctx -> {
            for (Variant v : VARIANTS) {
                ctx.addModel(v.key(), SimpleUnbakedExtraModel.blockStateModel(v.modelId()));
            }
            ctx.modifyBlockModelAfterBake().register((model, context) -> {
                Map<Season, ExtraModelKey<BlockStateModel>> bySeason = BY_BLOCK.get(context.state().getBlock());
                return bySeason == null ? model : new SeasonalLeaves(model, bySeason);
            });
        });
    }

    private static BlockStateModel baked(ExtraModelKey<BlockStateModel> key) {
        return ((FabricModelManager) Minecraft.getInstance().getModelManager()).getModel(key);
    }

    /** Delegates to the active season's recolored model when one exists, otherwise the vanilla model. */
    private record SeasonalLeaves(BlockStateModel vanilla,
                                  Map<Season, ExtraModelKey<BlockStateModel>> bySeason)
        implements BlockStateModel {
        private BlockStateModel current() {
            Season season = FallowClientSeasons.swapSeason();
            if (season != null) {
                ExtraModelKey<BlockStateModel> key = bySeason.get(season);
                if (key != null) {
                    BlockStateModel swapped = baked(key);
                    if (swapped != null) {
                        return swapped;
                    }
                }
            }
            return vanilla;
        }

        @Override
        public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
            current().collectParts(random, parts);
        }

        @Override
        public Material.Baked particleMaterial() {
            return current().particleMaterial();
        }

        @Override
        public int materialFlags() {
            return current().materialFlags();
        }
    }
}
