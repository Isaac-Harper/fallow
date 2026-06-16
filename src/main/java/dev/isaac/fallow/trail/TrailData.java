package dev.isaac.fallow.trail;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-dimension trail wear: one compact SavedData map ({@code BlockPos.asLong() -> wear}),
 * hard-capped by config (the system prunes lowest-wear entries on overflow). This is the
 * same shape Gaia's Breath persists for its soil-wear system - no per-block block entities,
 * no chunk attachments, save-safe and removable.
 */
public final class TrailData extends SavedData {
    public static final Codec<TrailData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
        .xmap(TrailData::fromStringMap, TrailData::toStringMap)
        .fieldOf("wear").codec();

    public static final SavedDataType<TrailData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("fallow", "trails"),
        TrailData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES
    );

    public final Long2IntOpenHashMap wear = new Long2IntOpenHashMap();

    public TrailData() {
    }

    public static TrailData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private static TrailData fromStringMap(Map<String, Integer> in) {
        TrailData data = new TrailData();
        for (Map.Entry<String, Integer> e : in.entrySet()) {
            try {
                data.wear.put(Long.parseLong(e.getKey()), e.getValue().intValue());
            } catch (NumberFormatException ignored) {
                // tolerate hand-edited junk
            }
        }
        return data;
    }

    private static Map<String, Integer> toStringMap(TrailData data) {
        Map<String, Integer> out = new HashMap<>(data.wear.size());
        data.wear.forEach((k, v) -> out.put(Long.toString(k), v));
        return out;
    }
}
