package dev.isaac.fallow.client;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.FallowConfig;
import dev.isaac.fallow.season.SeasonService;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Vanilla-widget config screen reachable from Mod Menu ({@link ModMenuIntegration}); same
 * pattern as Shulker Pocket's (working copies, commit + save only on <em>Done</em>, no custom
 * rendering). Two columns: ecology on the left, seasons on the right. The JSON file holds more
 * knobs (per-season multipliers and day portions, scheduler budget) for hand-tuning.
 *
 * <p>The config is server-side: edits apply immediately in singleplayer (same JVM); on a
 * dedicated server edit the file and run {@code /fallow reload}.
 */
public final class ConfigScreen extends Screen {
    private static final int WIDGET_WIDTH = 200;
    private static final int WIDGET_HEIGHT = 20;
    private static final int ROW_SPACING = 24;
    private static final int COLUMN_GAP = 10;
    private static final int ROWS = 9;

    private final Screen parent;

    // Working copies, seeded from the live config and committed on Done.
    private boolean vegetationEnabled;
    private double shortGrassChance;
    private double tallGrassChance;
    private double flowerChance;
    private double bushChance;
    private boolean trailsEnabled;
    private boolean diebackEnabled;
    private boolean saplingsEnabled;
    private boolean leafLitterEnabled;
    private boolean overcrowdingEnabled;
    private boolean shorelineEnabled;
    private boolean flowerWiltEnabled;
    private boolean visualsEnabled;
    private boolean seasonsEnabled;
    private int daysPerSeason;
    private boolean dayNightEnabled;
    private double summerDayPortion;
    private double winterDayPortion;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("fallow.config.title"));
        this.parent = parent;
        seedFrom(Fallow.CONFIG);
    }

    private void seedFrom(FallowConfig cfg) {
        this.vegetationEnabled = cfg.vegetation.enabled;
        this.shortGrassChance = cfg.vegetation.shortGrassChance;
        this.tallGrassChance = cfg.vegetation.tallGrassChance;
        this.flowerChance = cfg.vegetation.flowerChance;
        this.bushChance = cfg.vegetation.bushChance;
        this.trailsEnabled = cfg.trails.enabled;
        this.diebackEnabled = cfg.dieback.enabled;
        this.saplingsEnabled = cfg.saplings.enabled;
        this.leafLitterEnabled = cfg.leafLitter.enabled;
        this.overcrowdingEnabled = cfg.overcrowding.enabled;
        this.shorelineEnabled = cfg.shoreline.enabled;
        this.flowerWiltEnabled = cfg.flowerWilt.enabled;
        this.visualsEnabled = cfg.visuals.enabled;
        this.seasonsEnabled = cfg.seasons.enabled;
        this.daysPerSeason = cfg.seasons.daysPerSeason;
        this.dayNightEnabled = cfg.dayNight.enabled;
        this.summerDayPortion = cfg.dayNight.summerDayPortion;
        this.winterDayPortion = cfg.dayNight.winterDayPortion;
    }

    private static Tooltip tip(String key) {
        return Tooltip.create(Component.translatable(key));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int leftX = cx - WIDGET_WIDTH - COLUMN_GAP / 2;
        int rightX = cx + COLUMN_GAP / 2;
        int top = this.height / 2 - (ROWS * ROW_SPACING) / 2;

        StringWidget titleWidget = new StringWidget(this.title, this.font);
        titleWidget.setX(cx - titleWidget.getWidth() / 2);
        titleWidget.setY(top - 28);
        addRenderableWidget(titleWidget);

        // --- Left column: ecology ---
        int y = top;
        addRenderableWidget(CycleButton.onOffBuilder(this.vegetationEnabled)
            .withTooltip(v -> tip("fallow.config.vegetation.tooltip"))
            .create(leftX, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                Component.translatable("fallow.config.vegetation"),
                (button, value) -> this.vegetationEnabled = value));
        y += ROW_SPACING;

        addRenderableWidget(new PercentSlider(leftX, y, "fallow.config.short_grass", 0.10,
            () -> shortGrassChance, v -> shortGrassChance = v));
        y += ROW_SPACING;
        addRenderableWidget(new PercentSlider(leftX, y, "fallow.config.tall_grass", 0.10,
            () -> tallGrassChance, v -> tallGrassChance = v));
        y += ROW_SPACING;
        addRenderableWidget(new PercentSlider(leftX, y, "fallow.config.flower", 0.05,
            () -> flowerChance, v -> flowerChance = v));
        y += ROW_SPACING;
        addRenderableWidget(new PercentSlider(leftX, y, "fallow.config.bush", 0.05,
            () -> bushChance, v -> bushChance = v));
        y += ROW_SPACING;
        addRenderableWidget(CycleButton.onOffBuilder(this.diebackEnabled)
            .withTooltip(v -> tip("fallow.config.dieback.tooltip"))
            .create(leftX, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                Component.translatable("fallow.config.dieback"),
                (button, value) -> this.diebackEnabled = value));
        y += ROW_SPACING;
        addRenderableWidget(CycleButton.onOffBuilder(this.trailsEnabled)
            .withTooltip(v -> tip("fallow.config.trails.tooltip"))
            .create(leftX, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                Component.translatable("fallow.config.trails"),
                (button, value) -> this.trailsEnabled = value));
        y += ROW_SPACING;
        addRenderableWidget(CycleButton.onOffBuilder(this.leafLitterEnabled)
            .withTooltip(v -> tip("fallow.config.leaf_litter.tooltip"))
            .create(leftX, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                Component.translatable("fallow.config.leaf_litter"),
                (button, value) -> this.leafLitterEnabled = value));
        y += ROW_SPACING;
        addRenderableWidget(CycleButton.onOffBuilder(this.overcrowdingEnabled)
            .withTooltip(v -> tip("fallow.config.overcrowding.tooltip"))
            .create(leftX, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                Component.translatable("fallow.config.overcrowding"),
                (button, value) -> this.overcrowdingEnabled = value));

        // --- Right column: seasons ---
        y = top;
        addRenderableWidget(CycleButton.onOffBuilder(this.seasonsEnabled)
            .withTooltip(v -> tip("fallow.config.seasons.tooltip"))
            .create(rightX, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                Component.translatable("fallow.config.seasons"),
                (button, value) -> this.seasonsEnabled = value));
        y += ROW_SPACING;

        addRenderableWidget(new IntSlider(rightX, y, "fallow.config.season_length", 1, 30,
            () -> daysPerSeason, v -> daysPerSeason = v));
        y += ROW_SPACING;

        addRenderableWidget(CycleButton.onOffBuilder(this.visualsEnabled)
            .withTooltip(v -> tip("fallow.config.visuals.tooltip"))
            .create(rightX, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                Component.translatable("fallow.config.visuals"),
                (button, value) -> this.visualsEnabled = value));
        y += ROW_SPACING;

        addRenderableWidget(CycleButton.onOffBuilder(this.dayNightEnabled)
            .withTooltip(v -> tip("fallow.config.day_night.tooltip"))
            .create(rightX, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                Component.translatable("fallow.config.day_night"),
                (button, value) -> this.dayNightEnabled = value));
        y += ROW_SPACING;

        addRenderableWidget(new PortionSlider(rightX, y, "fallow.config.summer_day", 0.50, 0.75,
            () -> summerDayPortion, v -> summerDayPortion = v));
        y += ROW_SPACING;
        addRenderableWidget(new PortionSlider(rightX, y, "fallow.config.winter_day", 0.25, 0.50,
            () -> winterDayPortion, v -> winterDayPortion = v));
        y += ROW_SPACING;
        addRenderableWidget(CycleButton.onOffBuilder(this.saplingsEnabled)
            .withTooltip(v -> tip("fallow.config.saplings.tooltip"))
            .create(rightX, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                Component.translatable("fallow.config.saplings"),
                (button, value) -> this.saplingsEnabled = value));
        y += ROW_SPACING;
        addRenderableWidget(CycleButton.onOffBuilder(this.shorelineEnabled)
            .withTooltip(v -> tip("fallow.config.shoreline.tooltip"))
            .create(rightX, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                Component.translatable("fallow.config.shoreline"),
                (button, value) -> this.shorelineEnabled = value));
        y += ROW_SPACING;
        addRenderableWidget(CycleButton.onOffBuilder(this.flowerWiltEnabled)
            .withTooltip(v -> tip("fallow.config.flower_wilt.tooltip"))
            .create(rightX, y, WIDGET_WIDTH, WIDGET_HEIGHT,
                Component.translatable("fallow.config.flower_wilt"),
                (button, value) -> this.flowerWiltEnabled = value));

        int by = this.height - 28;
        addRenderableWidget(Button.builder(Component.translatable("fallow.config.reset"),
                b -> resetDefaults())
            .bounds(cx - 102, by - 24, 204, WIDGET_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
            .bounds(cx - 104, by, 100, WIDGET_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> { commit(); onClose(); })
            .bounds(cx + 4, by, 100, WIDGET_HEIGHT).build());
    }

    private void resetDefaults() {
        seedFrom(new FallowConfig());
        rebuildWidgets(); // re-init so every widget reflects the restored defaults
    }

    private void commit() {
        FallowConfig cfg = Fallow.CONFIG;
        cfg.vegetation.enabled = this.vegetationEnabled;
        cfg.vegetation.shortGrassChance = this.shortGrassChance;
        cfg.vegetation.tallGrassChance = this.tallGrassChance;
        cfg.vegetation.flowerChance = this.flowerChance;
        cfg.vegetation.bushChance = this.bushChance;
        cfg.trails.enabled = this.trailsEnabled;
        cfg.dieback.enabled = this.diebackEnabled;
        cfg.saplings.enabled = this.saplingsEnabled;
        cfg.leafLitter.enabled = this.leafLitterEnabled;
        cfg.overcrowding.enabled = this.overcrowdingEnabled;
        cfg.shoreline.enabled = this.shorelineEnabled;
        cfg.flowerWilt.enabled = this.flowerWiltEnabled;
        cfg.visuals.enabled = this.visualsEnabled;
        cfg.seasons.enabled = this.seasonsEnabled;
        cfg.seasons.daysPerSeason = this.daysPerSeason;
        cfg.dayNight.enabled = this.dayNightEnabled;
        cfg.dayNight.summerDayPortion = this.summerDayPortion;
        cfg.dayNight.winterDayPortion = this.winterDayPortion;
        cfg.clamp();
        cfg.save();
        SeasonService.invalidate(); // singleplayer: re-apply the clock rate next tick
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    /** Slider over [0, max] probability, shown as a percentage with 0.05 % steps. */
    private static final class PercentSlider extends AbstractSliderButton {
        private final String key;
        private final double max;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;

        PercentSlider(int x, int y, String key, double max, DoubleSupplier getter, DoubleConsumer setter) {
            super(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.empty(), getter.getAsDouble() / max);
            this.key = key;
            this.max = max;
            this.getter = getter;
            this.setter = setter;
            setTooltip(tip(key + ".tooltip"));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(key, String.format("%.2f", getter.getAsDouble() * 100)));
        }

        @Override
        protected void applyValue() {
            double stepped = Math.round(this.value * max / 0.0005) * 0.0005;
            setter.accept(Math.max(0.0, Math.min(max, stepped)));
        }
    }

    /** Slider over an int range. */
    private static final class IntSlider extends AbstractSliderButton {
        private final String key;
        private final int min;
        private final int max;
        private final java.util.function.IntSupplier getter;
        private final java.util.function.IntConsumer setter;

        IntSlider(int x, int y, String key, int min, int max,
                  java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
            super(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.empty(),
                (getter.getAsInt() - min) / (double) (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.setter = setter;
            setTooltip(tip(key + ".tooltip"));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(key, getter.getAsInt()));
        }

        @Override
        protected void applyValue() {
            setter.accept(min + (int) Math.round(this.value * (max - min)));
        }
    }

    /** Slider over a [min, max] day-portion range, shown as a percentage of the cycle. */
    private static final class PortionSlider extends AbstractSliderButton {
        private final String key;
        private final double min;
        private final double max;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;

        PortionSlider(int x, int y, String key, double min, double max,
                      DoubleSupplier getter, DoubleConsumer setter) {
            super(x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Component.empty(),
                (clamp(getter.getAsDouble(), min, max) - min) / (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.setter = setter;
            setTooltip(tip(key + ".tooltip"));
            updateMessage();
        }

        private static double clamp(double v, double min, double max) {
            return Math.max(min, Math.min(max, v));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(key, String.format("%.1f", getter.getAsDouble() * 100)));
        }

        @Override
        protected void applyValue() {
            double stepped = Math.round((min + this.value * (max - min)) / 0.005) * 0.005;
            setter.accept(clamp(stepped, min, max));
        }
    }
}
