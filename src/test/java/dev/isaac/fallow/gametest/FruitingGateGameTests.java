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
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * In-world coverage for the gates that stop {@link FruitDropTask} from dropping fruit: the crop
 * layer toggle (fallow-namespace fruit requires crops on), the per-type season gate, and the
 * non-persistent leaf requirement. Each runs the task many times and asserts that no fruit item
 * ever spawns, the mirror of the positive drop tests in the other suites.
 *
 * <p>As with the other suites, {@code Fallow.CONFIG} is a shared mutable static, so every test sets
 * every field it depends on at the start rather than trusting defaults left by a prior test.
 */
public class FruitingGateGameTests {
    private static final BlockPos GROUND = new BlockPos(1, 1, 1);

    /** Where the fruiting canopy sits, a few blocks above the ground with open air beneath. */
    private static final BlockPos CANOPY = new BlockPos(1, 4, 1);

    private static void withSeason(GameTestHelper helper, Season season, Runnable body) {
        SeasonState.get(helper.getLevel().getServer()).set(season, 0);
        SeasonService.invalidate();
        helper.runAfterDelay(1, body);
    }

    /** Run the fruiting task over the ground chunk the requested number of times. */
    private static int runTask(GameTestHelper helper, ServerLevel level, int passes) {
        LevelChunk chunk = level.getChunkAt(helper.absolutePos(GROUND));
        FruitDropTask task = new FruitDropTask(Fallow.GROWTH_RATES);
        int dropped = 0;
        for (int i = 0; i < passes; i++) {
            dropped += task.visitChunk(level, chunk, level.getRandom(), 2000);
        }
        return dropped;
    }

    // 7. With crops disabled, a fallow-namespace fruit (cherries) never drops even at chance 1.0.
    @GameTest(environment = "fallow:fruitgate_spring", maxTicks = 100, skyAccess = true)
    public void fruitDropSkippedWhenCropsDisabled(GameTestHelper helper) {
        Fallow.CONFIG = new FallowConfig();
        Fallow.CONFIG.enabled = true;
        Fallow.CONFIG.crops.enabled = false;
        Fallow.CONFIG.fruiting.enabled = true;
        Fallow.CONFIG.seasons.enabled = true;
        Fallow.CONFIG.fruiting.types.get("minecraft:cherry_leaves").chance = 1.0;
        ServerLevel level = helper.getLevel();

        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CANOPY, Blocks.CHERRY_LEAVES.defaultBlockState()
            .setValue(BlockStateProperties.PERSISTENT, false));

        withSeason(helper, Season.SPRING, () -> {
            int dropped = runTask(helper, level, 50);
            helper.assertTrue(dropped == 0,
                "cherry fruit must not drop while the crop layer is disabled");
            helper.assertItemEntityNotPresent(FallowItems.CHERRIES);
            helper.succeed();
        });
    }

    // 13. Out-of-season cherry leaves (spring fruit, autumn now) never drop, even at chance 1.0.
    @GameTest(environment = "fallow:fruitgate_autumn", maxTicks = 100, skyAccess = true)
    public void fruitDropSkipsOutOfSeasonLeaf(GameTestHelper helper) {
        Fallow.CONFIG = new FallowConfig();
        Fallow.CONFIG.enabled = true;
        Fallow.CONFIG.crops.enabled = true;
        Fallow.CONFIG.fruiting.enabled = true;
        Fallow.CONFIG.seasons.enabled = true;
        Fallow.CONFIG.fruiting.types.get("minecraft:cherry_leaves").chance = 1.0;
        ServerLevel level = helper.getLevel();

        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CANOPY, Blocks.CHERRY_LEAVES.defaultBlockState()
            .setValue(BlockStateProperties.PERSISTENT, false));

        withSeason(helper, Season.AUTUMN, () -> {
            int dropped = runTask(helper, level, 50);
            helper.assertTrue(dropped == 0,
                "cherry fruit must not drop out of its season");
            helper.assertItemEntityNotPresent(FallowItems.CHERRIES);
            helper.succeed();
        });
    }

    // 14. Persistent (player-placed) cherry leaves never fruit, even in season at chance 1.0.
    @GameTest(environment = "fallow:fruitgate_persistent", maxTicks = 100, skyAccess = true)
    public void fruitDropSkipsPersistentLeaf(GameTestHelper helper) {
        Fallow.CONFIG = new FallowConfig();
        Fallow.CONFIG.enabled = true;
        Fallow.CONFIG.crops.enabled = true;
        Fallow.CONFIG.fruiting.enabled = true;
        Fallow.CONFIG.seasons.enabled = true;
        Fallow.CONFIG.fruiting.types.get("minecraft:cherry_leaves").chance = 1.0;
        ServerLevel level = helper.getLevel();

        helper.setBlock(GROUND, Blocks.DIRT);
        helper.setBlock(CANOPY, Blocks.CHERRY_LEAVES.defaultBlockState()
            .setValue(BlockStateProperties.PERSISTENT, true));

        withSeason(helper, Season.SPRING, () -> {
            int dropped = runTask(helper, level, 50);
            helper.assertTrue(dropped == 0,
                "persistent cherry leaves must not fruit");
            helper.assertItemEntityNotPresent(FallowItems.CHERRIES);
            helper.succeed();
        });
    }
}
