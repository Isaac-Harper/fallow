package dev.isaac.fallow.notice;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Remembers which players have already seen Fallow's one-time first-join notice, so it shows once
 * per player and never nags. One small overworld SavedData (a set of player UUIDs); like the rest
 * of the mod's state it is save-safe and leaves nothing behind if the mod is removed.
 */
public final class NoticeData extends SavedData {
    public static final Codec<NoticeData> CODEC = Codec.STRING.listOf()
        .xmap(NoticeData::fromList, NoticeData::toList)
        .fieldOf("warned").codec();

    public static final SavedDataType<NoticeData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("fallow", "notice"),
        NoticeData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES
    );

    public final Set<UUID> warned = new HashSet<>();

    public NoticeData() {
    }

    public static NoticeData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private static NoticeData fromList(List<String> in) {
        NoticeData data = new NoticeData();
        for (String s : in) {
            try {
                data.warned.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // tolerate hand-edited junk
            }
        }
        return data;
    }

    private static List<String> toList(NoticeData data) {
        return data.warned.stream().map(UUID::toString).collect(Collectors.toList());
    }
}
