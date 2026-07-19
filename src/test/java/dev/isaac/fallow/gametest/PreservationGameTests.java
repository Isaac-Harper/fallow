package dev.isaac.fallow.gametest;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.api.Season;
import dev.isaac.fallow.ecology.FruitDropTask;
import dev.isaac.fallow.item.FallowItems;
import dev.isaac.fallow.season.SeasonService;
import dev.isaac.fallow.season.SeasonState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * In-world coverage for the harvest/preservation products layer: seasonal fruit drops, the crop
 * layer's grass seed injection, and the jar-return consumption remainder.
 *
 * <p>As with the other suites, {@code Fallow.CONFIG} is a shared mutable static, so every test sets
 * every field it depends on at the start rather than trusting defaults left by a prior test.
 */
public class PreservationGameTests {
    private static final BlockPos GROUND = new BlockPos(1, 1, 1);

    private static void withSeason(GameTestHelper helper, Season season, Runnable body) {
        SeasonState.get(helper.getLevel().getServer()).set(season, 0);
        SeasonService.invalidate();
        helper.runAfterDelay(1, body);
    }

    // 1. Non-persistent flowering azalea leaves drop a plum in autumn.
    @GameTest(environment = "fallow:autumn", maxTicks = 100, skyAccess = true)
    public void plumDropsFromAzaleaLeaves(GameTestHelper helper) {
        Fallow.CONFIG = new FallowConfig();
        Fallow.CONFIG.enabled = true;
        Fallow.CONFIG.crops.enabled = true;
        Fallow.CONFIG.fruiting.enabled = true;
        Fallow.CONFIG.seasons.enabled = true;
        Fallow.CONFIG.fruiting.types.get("minecraft:flowering_azalea_leaves").chance = 1.0;
        ServerLevel level = helper.getLevel();

        helper.setBlock(GROUND, Blocks.DIRT);
        // Natural (non-persistent) flowering azalea leaves a few blocks up, with open air beneath
        // them so the task has a spot to drop into. skyAccess keeps the heightmap on the dirt.
        helper.setBlock(new BlockPos(1, 4, 1), Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState()
            .setValue(BlockStateProperties.PERSISTENT, false));

        withSeason(helper, Season.AUTUMN, () -> {
            LevelChunk chunk = level.getChunkAt(helper.absolutePos(GROUND));
            FruitDropTask task = new FruitDropTask(Fallow.GROWTH_RATES);
            // The provider stack (biome growth multiplier) can scale the effective chance below 1.0,
            // so loop until a drop lands rather than asserting on a single probabilistic call.
            int dropped = 0;
            for (int attempt = 0; attempt < 50 && dropped == 0; attempt++) {
                dropped += task.visitChunk(level, chunk, level.getRandom(), 2000);
            }
            helper.assertTrue(dropped > 0, "flowering azalea leaves should have dropped fruit");
            helper.assertItemEntityPresent(FallowItems.PLUM);
            helper.succeed();
        });
    }

    // 2. Grass seed drops honour the crops toggle: on -> a fallow seed can drop, off -> never.
    @GameTest(environment = "fallow:noseason")
    public void grassSeedLootRespectsCropsToggle(GameTestHelper helper) {
        Fallow.CONFIG = new FallowConfig();
        Fallow.CONFIG.enabled = true;
        ServerLevel level = helper.getLevel();

        // The seed pool is baked into the already-loaded short_grass table at server start with the
        // default seedDropChance (0.05). The CropsEnabledCondition on that pool re-reads
        // Fallow.CONFIG.crops.enabled on every evaluation, so flipping the flag steers the rolls
        // without a table reload. At 0.05/roll a false negative over 500 rolls is ~7e-12, so the
        // "at least one" assertion is deterministic in practice.
        ResourceKey<LootTable> shortGrass = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.withDefaultNamespace("blocks/short_grass"));
        LootTable table = level.getServer().reloadableRegistries().getLootTable(shortGrass);

        BlockPos originAbs = helper.absolutePos(GROUND);
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(originAbs))
            .withParameter(LootContextParams.BLOCK_STATE, Blocks.SHORT_GRASS.defaultBlockState())
            .withParameter(LootContextParams.TOOL, new ItemStack(Items.IRON_HOE))
            .create(LootContextParamSets.BLOCK);

        // With the crop layer on, at least one roll drops a fallow seed.
        Fallow.CONFIG.crops.enabled = true;
        boolean sawFallowSeedEnabled = false;
        for (int i = 0; i < 500 && !sawFallowSeedEnabled; i++) {
            if (rollHasFallowItem(table, params, level, i)) {
                sawFallowSeedEnabled = true;
            }
        }
        helper.assertTrue(sawFallowSeedEnabled,
            "a fallow seed should drop from short grass when crops are enabled");

        // With the crop layer off, no roll may drop a fallow seed.
        Fallow.CONFIG.crops.enabled = false;
        for (int i = 0; i < 500; i++) {
            helper.assertFalse(rollHasFallowItem(table, params, level, 1000 + i),
                "no fallow seed may drop from short grass when crops are disabled");
        }
        helper.succeed();
    }

    // 3. Jam returns a glass bottle when consumed (assert the use-remainder component; driving the
    // full vanilla consume loop from a gametest is impractical, so the component is asserted).
    @GameTest(environment = "fallow:noseason")
    public void jarItemsReturnBottle(GameTestHelper helper) {
        Fallow.CONFIG = new FallowConfig();
        Fallow.CONFIG.enabled = true;

        ItemStack jam = new ItemStack(FallowItems.JAM);
        UseRemainder remainder = jam.get(DataComponents.USE_REMAINDER);
        helper.assertTrue(remainder != null, "jam should carry a use-remainder component");
        ItemStack returned = remainder.convertInto().create();
        helper.assertTrue(returned.is(Items.GLASS_BOTTLE),
            "eating jam should return a glass bottle");
        helper.succeed();
    }

    /** True if this loot roll contains at least one item in the fallow namespace. */
    private static boolean rollHasFallowItem(LootTable table, LootParams params, ServerLevel level,
            long seed) {
        for (ItemStack stack : table.getRandomItems(params, seed)) {
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(Fallow.MOD_ID)) {
                return true;
            }
        }
        return false;
    }
}
