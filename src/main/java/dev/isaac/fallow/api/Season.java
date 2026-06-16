package dev.isaac.fallow.api;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The four seasons, in cycle order. Public API: safe for other mods to reference. */
public enum Season implements StringRepresentable {
    SPRING("spring"),
    SUMMER("summer"),
    AUTUMN("autumn"),
    WINTER("winter");

    public static final Codec<Season> CODEC = StringRepresentable.fromEnum(Season::values);

    private final String id;

    Season(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public Season next() {
        Season[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** This season shifted forward by {@code by} steps in the cycle (negative shifts back). */
    public Season shifted(int by) {
        Season[] values = values();
        return values[Math.floorMod(ordinal() + by, values.length)];
    }

    /** Lowercase id ("spring"), used by the /fallow command. */
    public String id() {
        return id;
    }

    public static Season byId(String id) {
        for (Season s : values()) {
            if (s.id.equalsIgnoreCase(id)) return s;
        }
        return null;
    }
}
