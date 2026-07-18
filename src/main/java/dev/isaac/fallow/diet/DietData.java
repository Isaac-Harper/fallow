package dev.isaac.fallow.diet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player diet windows, stored in the overworld SavedData. One entry per UUID; windows are
 * created on first access. Codec serializes through a string-keyed map of meal lists so the
 * format stays readable in NBT viewers.
 */
public final class DietData extends SavedData {

    // Codec for one Meal: {groups: [str, ...], day: long}
    private static final Codec<DietWindow.Meal> MEAL_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.list(Codec.STRING)
            .xmap(list -> (Set<String>) new LinkedHashSet<>(list),
                  set -> new ArrayList<>(set))
            .fieldOf("groups").forGetter(DietWindow.Meal::groups),
        Codec.LONG.fieldOf("day").forGetter(DietWindow.Meal::day)
    ).apply(inst, DietWindow.Meal::new));

    private static final Codec<DietWindow> WINDOW_CODEC =
        Codec.list(MEAL_CODEC).xmap(DietWindow::new, DietWindow::getMeals);

    public static final Codec<DietData> CODEC = Codec.unboundedMap(Codec.STRING, WINDOW_CODEC)
        .xmap(DietData::fromStringMap, DietData::toStringMap)
        .fieldOf("windows").codec();

    public static final SavedDataType<DietData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("fallow", "diet"),
        DietData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES
    );

    private final Map<UUID, DietWindow> windows = new HashMap<>();

    public DietData() {
    }

    /** Gets (or creates) the diet window for a player. */
    public DietWindow windowFor(UUID uuid) {
        return windows.computeIfAbsent(uuid, k -> new DietWindow());
    }

    /** Returns the diet data from the overworld storage, creating it if absent. */
    public static DietData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private static DietData fromStringMap(Map<String, DietWindow> in) {
        DietData data = new DietData();
        for (Map.Entry<String, DietWindow> e : in.entrySet()) {
            try {
                data.windows.put(UUID.fromString(e.getKey()), e.getValue());
            } catch (IllegalArgumentException ignored) {
                // tolerate hand-edited junk
            }
        }
        return data;
    }

    private static Map<String, DietWindow> toStringMap(DietData data) {
        Map<String, DietWindow> out = new HashMap<>(data.windows.size());
        data.windows.forEach((k, v) -> out.put(k.toString(), v));
        return out;
    }
}
