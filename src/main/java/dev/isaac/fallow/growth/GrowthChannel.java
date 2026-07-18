package dev.isaac.fallow.growth;

/**
 * One probability knob per ecology behavior. Each channel declares whether it models growth
 * or decay: seasons scale the two in opposite directions (spring boosts growth and slows
 * decay; winter slows growth and accelerates decay).
 */
public enum GrowthChannel {
    SHORT_GRASS(Kind.GROWTH),
    TALL_GRASS(Kind.GROWTH),
    FLOWER(Kind.GROWTH),
    BUSH(Kind.GROWTH),
    SAPLING(Kind.GROWTH),
    SUGAR_CANE(Kind.GROWTH),
    SEAGRASS(Kind.GROWTH),
    /** Bamboo creeps clonally. Scaled normally, so jungle's low seasonality keeps it aseasonal. */
    BAMBOO(Kind.GROWTH),
    /** Fruit drops. Season-exempt like SAPLING: each {@code fruiting.types} entry carries its own
     * season and per-type chance, applied in {@code FruitDropTask}; the provider stack contributes
     * the biome growth and heatwave scaling on top. */
    FRUIT(Kind.GROWTH),
    DIEBACK(Kind.DECAY),
    /** Forest floors build up in autumn/winter, hence a decay channel. */
    LEAF_LITTER(Kind.DECAY),
    /** Overcrowded grass thins toward a density target. Season-exempt: its season lives in the
     * per-season density target ({@code overcrowding.*Density}), not in this channel's rate. */
    CROWDING(Kind.DECAY),
    /** Flowers wilt through autumn and winter. */
    FLOWER_WILT(Kind.DECAY);

    public enum Kind {
        GROWTH,
        DECAY,
    }

    private final Kind kind;

    GrowthChannel(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
