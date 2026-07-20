package dev.isaac.fallow.gametest.client;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.block.CornCropBlock;
import dev.isaac.fallow.block.FallowBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestClientLevelContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The mod's first automated visual verification: launches a real client, opens a flat creative
 * singleplayer world, builds a small plot of representative crops at max age, teleports the player
 * 6 blocks in front of the row, hides the HUD, and captures a clean screenshot. Registered from the
 * dev-only {@code fallow_gametest} companion mod, so it never reaches the shipped jar.
 */
public class CropVisualTest implements FabricClientGameTest {
    /** Surface on a flat world is at y=-61; crops sit at y=-59 (one above farmland). */
    private static final int GROUND_Y = -60;
    /**
     * X origin of the 10-block crop row. Setting this to -5 (with ROW_WIDTH=10) puts the row
     * center at x=0, matching the camera's default x-position.
     */
    private static final int PLOT_X = -5;
    /** Z of the crop row; camera stands at PLOT_Z - 4 facing +Z into the row. */
    private static final int PLOT_Z = 0;
    /** Number of blocks in the row, used to center the camera. */
    private static final int ROW_WIDTH = 10;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getServer().runOnServer(server -> {
                Fallow.CONFIG = new FallowConfig();
                Fallow.CONFIG.enabled = true;
                Fallow.CONFIG.crops.enabled = true;

                ServerLevel overworld = server.overworld();
                buildPlot(overworld);

                // Teleport the player to stand 6 blocks south of the row center, facing north.
                // Eye height is 1.62 above feet; standing on GROUND_Y = -60 puts eyes at ~-58.
                // Row spans x = PLOT_X .. PLOT_X+ROW_WIDTH-1 = -5..4; center at -0.5.
                // Camera at x=0 looks slightly right of center; shift left by 0.5 to equalize.
                double cameraX = PLOT_X + ROW_WIDTH / 2.0 - 0.5;  // = -0.5
                double cameraZ = PLOT_Z - 7.0;
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.setGameMode(GameType.CREATIVE);
                    // Stand one block lower than ground so eye height (~1.62 above feet) sits at
                    // crop level; this fills the frame with crops rather than bed-top and sky.
                    player.teleportTo(cameraX, GROUND_Y - 1, cameraZ);
                }
            });

            TestClientLevelContext clientLevel = singleplayer.getClientLevel();
            clientLevel.waitForChunksRender();
            // Let the chunk geometry and lighting settle before aiming and shooting.
            context.waitTicks(20);

            // Hide HUD (chat, hotbar, crosshair) for a clean crop-only frame. 26.2 moved the flag
            // off Options.hideGui onto Hud (toggle only, so guard on isHidden to force the state).
            //? if >=26.2 {
            context.runOnClient(client -> {
                if (!client.gui.hud.isHidden()) {
                    client.gui.hud.toggle();
                }
            });
            //?} else {
            /*context.runOnClient(client -> client.options.hideGui = true);*/
            //?}

            // Aim toward the center of the row at crop height. The trellis vine and corn double
            // stalk are the tallest elements; aiming slightly above ground level keeps them in frame.
            // Row center is at x = PLOT_X + ROW_WIDTH/2 - 1 = -1 (left of BlockPos 0).
            context.getInput().lookAt(new BlockPos(PLOT_X + ROW_WIDTH / 2 - 1, GROUND_Y + 1, PLOT_Z));
            context.waitTicks(5);

            context.takeScreenshot(
                TestScreenshotOptions.of("fallow_crops_plot").withSize(854, 480));

            // Restore HUD so the runner's end-state check doesn't see a surprising client state.
            //? if >=26.2 {
            context.runOnClient(client -> {
                if (client.gui.hud.isHidden()) {
                    client.gui.hud.toggle();
                }
            });
            //?} else {
            /*context.runOnClient(client -> client.options.hideGui = false);*/
            //?}
        }
    }

    /** Lay a farmland row and set each representative crop at its mature state. */
    private static void buildPlot(ServerLevel level) {
        int x = PLOT_X;
        int y = GROUND_Y;
        int z = PLOT_Z;

        // Farmland row for the tilled crops (turnip, cabbage, onion).
        for (int i = 0; i < 3; i++) {
            place(level, new BlockPos(x + i, y, z), Blocks.FARMLAND.defaultBlockState());
        }
        place(level, new BlockPos(x, y + 1, z),     mature(FallowBlocks.TURNIP_CROP));
        place(level, new BlockPos(x + 1, y + 1, z), mature(FallowBlocks.CABBAGE_CROP));
        place(level, new BlockPos(x + 2, y + 1, z), mature(FallowBlocks.ONION_CROP));

        // Filler block between the farmland row and the trellis so the bed reads continuous.
        place(level, new BlockPos(x + 3, y, z), Blocks.DIRT.defaultBlockState());

        // Trellis with a mature pea vine climbing it.
        place(level, new BlockPos(x + 4, y, z), Blocks.DIRT.defaultBlockState());
        place(level, new BlockPos(x + 4, y + 1, z), FallowBlocks.TRELLIS.defaultBlockState());
        place(level, new BlockPos(x + 4, y + 2, z),
            FallowBlocks.PEA_CROP.defaultBlockState()
                .setValue(BlockStateProperties.AGE_3, 3));

        // Strawberry bush at max age (3).
        place(level, new BlockPos(x + 5, y, z), Blocks.GRASS_BLOCK.defaultBlockState());
        place(level, new BlockPos(x + 5, y + 1, z),
            FallowBlocks.STRAWBERRY_BUSH.defaultBlockState()
                .setValue(BlockStateProperties.AGE_3, 3));

        // Corn double stalk: mature lower half then upper half above it.
        place(level, new BlockPos(x + 6, y, z), Blocks.DIRT.defaultBlockState());
        place(level, new BlockPos(x + 6, y + 1, z),
            FallowBlocks.CORN_CROP.defaultBlockState()
                .setValue(CornCropBlock.AGE, 3)
                .setValue(CornCropBlock.HALF, DoubleBlockHalf.LOWER));
        place(level, new BlockPos(x + 6, y + 2, z),
            FallowBlocks.CORN_CROP.defaultBlockState()
                .setValue(CornCropBlock.AGE, 3)
                .setValue(CornCropBlock.HALF, DoubleBlockHalf.UPPER));

        // Squash fruit with an attached stem pointing at it.
        place(level, new BlockPos(x + 7, y, z), Blocks.DIRT.defaultBlockState());
        place(level, new BlockPos(x + 7, y + 1, z), FallowBlocks.SQUASH.defaultBlockState());
        place(level, new BlockPos(x + 8, y, z), Blocks.FARMLAND.defaultBlockState());
        place(level, new BlockPos(x + 8, y + 1, z),
            FallowBlocks.ATTACHED_SQUASH_STEM.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));

        // Wild onion: decorative forage plant with no growth stage.
        place(level, new BlockPos(x + 9, y, z), Blocks.GRASS_BLOCK.defaultBlockState());
        place(level, new BlockPos(x + 9, y + 1, z),
            FallowBlocks.WILD_ONION.defaultBlockState());
    }

    private static BlockState mature(Block crop) {
        return crop.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3);
    }

    /**
     * Place a block without neighbour updates so crops' own survival checks cannot yank them back
     * out before the frame is captured; clients still sync for rendering.
     */
    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
