package dev.isaac.fallow.client;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Vanilla-widget config screen reachable from Mod Menu ({@link ModMenuIntegration}); working
 * copies, committed only on <em>Done</em>. A master switch across the top, then two columns of
 * feature on/off toggles. Numeric fine-tuning (per-channel chances, season length, day portions,
 * and the many per-biome maps) lives in {@code config/fallow.json} and is documented in
 * docs/configuration.md, so this screen stays uncluttered and fits any GUI scale.
 *
 * <p>The config is server-side: edits apply immediately in singleplayer (same JVM); on a
 * dedicated server edit the file and run {@code /fallow reload}.
 */
public final class ConfigScreen extends Screen {
    private static final int WIDGET_WIDTH = 200;
    private static final int WIDGET_HEIGHT = 20;
    private static final int ROW_SPACING = 30;
    private static final int COLUMN_GAP = 10;
    /** Most rows in either column (left has 5, right has 8); drives the fit-to-window spacing. */
    private static final int ROWS = 8;

    private final Screen parent;

    // Working copies, seeded from the live config and committed on Done.
    private boolean masterEnabled;
    private boolean vegetationEnabled;
    private boolean diebackEnabled;
    private boolean trailsEnabled;
    private boolean leafLitterEnabled;
    private boolean overcrowdingEnabled;
    private boolean seasonsEnabled;
    private boolean visualsEnabled;
    private boolean dayNightEnabled;
    private boolean saplingsEnabled;
    private boolean shorelineEnabled;
    private boolean flowerWiltEnabled;
    private boolean cropsEnabled;
    private boolean dietEnabled;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("fallow.config.title"));
        this.parent = parent;
        seedFrom(Fallow.CONFIG);
    }

    private void seedFrom(FallowConfig cfg) {
        this.masterEnabled = cfg.enabled;
        this.vegetationEnabled = cfg.vegetation.enabled;
        this.diebackEnabled = cfg.dieback.enabled;
        this.trailsEnabled = cfg.trails.enabled;
        this.leafLitterEnabled = cfg.leafLitter.enabled;
        this.overcrowdingEnabled = cfg.overcrowding.enabled;
        this.seasonsEnabled = cfg.seasons.enabled;
        this.visualsEnabled = cfg.visuals.enabled;
        this.dayNightEnabled = cfg.dayNight.enabled;
        this.saplingsEnabled = cfg.saplings.enabled;
        this.shorelineEnabled = cfg.shoreline.enabled;
        this.flowerWiltEnabled = cfg.flowerWilt.enabled;
        this.cropsEnabled = cfg.crops.enabled;
        this.dietEnabled = cfg.diet.enabled;
    }

    private static Tooltip tip(String key) {
        return Tooltip.create(Component.translatable(key));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int leftX = cx - WIDGET_WIDTH - COLUMN_GAP / 2;
        int rightX = cx + COLUMN_GAP / 2;
        int fullWidth = WIDGET_WIDTH * 2 + COLUMN_GAP;

        // Top-anchored so the title is always visible: title, full-width master switch, two columns;
        // one row of action buttons pinned to the bottom.
        StringWidget titleWidget = new StringWidget(this.title, this.font);
        titleWidget.setX(cx - titleWidget.getWidth() / 2);
        titleWidget.setY(8);
        addRenderableWidget(titleWidget);

        int masterY = 22;
        addRenderableWidget(CycleButton.onOffBuilder(this.masterEnabled)
            .withTooltip(v -> tip("fallow.config.enabled.tooltip"))
            .create(leftX, masterY, fullWidth, WIDGET_HEIGHT,
                Component.translatable("fallow.config.enabled"),
                (button, value) -> this.masterEnabled = value));

        // The columns fill the space between the master switch and the bottom buttons; with so few
        // rows the spacing stays at the full ROW_SPACING on any normal window and only compresses on
        // a very short one, so rows are always padded and never overlap the buttons.
        int by = this.height - 28;
        int regionTop = masterY + WIDGET_HEIGHT + 12;
        int rowSpacing = Math.max(WIDGET_HEIGHT,
            Math.min(ROW_SPACING, ((by - 8 - regionTop) - WIDGET_HEIGHT) / (ROWS - 1)));
        int blockHeight = (ROWS - 1) * rowSpacing + WIDGET_HEIGHT;
        int columnsTop = regionTop + Math.max(0, (by - 8 - regionTop - blockHeight) / 2);

        // --- Left column: ecology ---
        int y = columnsTop;
        y = toggle(leftX, y, rowSpacing, "fallow.config.vegetation", this.vegetationEnabled, v -> this.vegetationEnabled = v);
        y = toggle(leftX, y, rowSpacing, "fallow.config.dieback", this.diebackEnabled, v -> this.diebackEnabled = v);
        y = toggle(leftX, y, rowSpacing, "fallow.config.trails", this.trailsEnabled, v -> this.trailsEnabled = v);
        y = toggle(leftX, y, rowSpacing, "fallow.config.leaf_litter", this.leafLitterEnabled, v -> this.leafLitterEnabled = v);
        toggle(leftX, y, rowSpacing, "fallow.config.overcrowding", this.overcrowdingEnabled, v -> this.overcrowdingEnabled = v);

        // --- Right column: seasons ---
        y = columnsTop;
        y = toggle(rightX, y, rowSpacing, "fallow.config.seasons", this.seasonsEnabled, v -> this.seasonsEnabled = v);
        y = toggle(rightX, y, rowSpacing, "fallow.config.visuals", this.visualsEnabled, v -> this.visualsEnabled = v);
        y = toggle(rightX, y, rowSpacing, "fallow.config.day_night", this.dayNightEnabled, v -> this.dayNightEnabled = v);
        y = toggle(rightX, y, rowSpacing, "fallow.config.saplings", this.saplingsEnabled, v -> this.saplingsEnabled = v);
        y = toggle(rightX, y, rowSpacing, "fallow.config.shoreline", this.shorelineEnabled, v -> this.shorelineEnabled = v);
        y = toggle(rightX, y, rowSpacing, "fallow.config.flower_wilt", this.flowerWiltEnabled, v -> this.flowerWiltEnabled = v);
        y = toggle(rightX, y, rowSpacing, "fallow.config.crops", this.cropsEnabled, v -> this.cropsEnabled = v);
        toggle(rightX, y, rowSpacing, "fallow.config.diet", this.dietEnabled, v -> this.dietEnabled = v);

        // Reset | Cancel | Done on one bottom row.
        addRenderableWidget(Button.builder(Component.translatable("fallow.config.reset"), b -> resetDefaults())
            .bounds(leftX, by, 160, WIDGET_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
            .bounds(leftX + 165, by, 120, WIDGET_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> { commit(); onClose(); })
            .bounds(leftX + 290, by, fullWidth - 290, WIDGET_HEIGHT).build());
    }

    /** Add an on/off toggle at {@code y} and return the next row's y. */
    private int toggle(int x, int y, int rowSpacing, String key, boolean value,
                       java.util.function.Consumer<Boolean> setter) {
        addRenderableWidget(CycleButton.onOffBuilder(value)
            .withTooltip(v -> tip(key + ".tooltip"))
            .create(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.translatable(key),
                (button, v) -> setter.accept(v)));
        return y + rowSpacing;
    }

    private void resetDefaults() {
        seedFrom(new FallowConfig());
        rebuildWidgets(); // re-init so every widget reflects the restored defaults
    }

    private void commit() {
        // Never mutate the live config in place (Fallow.CONFIG is replaced wholesale and the
        // server thread reads it concurrently): apply the toggles to a fresh copy of the file,
        // save, and route through the same reload path as /fallow reload.
        FallowConfig cfg = FallowConfig.load();
        cfg.enabled = this.masterEnabled;
        cfg.vegetation.enabled = this.vegetationEnabled;
        cfg.dieback.enabled = this.diebackEnabled;
        cfg.trails.enabled = this.trailsEnabled;
        cfg.leafLitter.enabled = this.leafLitterEnabled;
        cfg.overcrowding.enabled = this.overcrowdingEnabled;
        cfg.seasons.enabled = this.seasonsEnabled;
        cfg.visuals.enabled = this.visualsEnabled;
        cfg.dayNight.enabled = this.dayNightEnabled;
        cfg.saplings.enabled = this.saplingsEnabled;
        cfg.shoreline.enabled = this.shorelineEnabled;
        cfg.flowerWilt.enabled = this.flowerWiltEnabled;
        cfg.crops.enabled = this.cropsEnabled;
        cfg.diet.enabled = this.dietEnabled;
        cfg.clamp();
        cfg.save();
        Fallow.reload(this.minecraft.getSingleplayerServer()); // null outside singleplayer
        // The tint parameters read the visuals config directly, so a toggle must recompute now
        // rather than wait for the next season payload (up to an in-game day away).
        FallowClientSeasons.refresh();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
