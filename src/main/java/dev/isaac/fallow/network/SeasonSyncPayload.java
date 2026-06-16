package dev.isaac.fallow.network;

import dev.isaac.fallow.Fallow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One S2C payload syncing season state to clients (sent on join and whenever the state
 * changes - a handful of packets per in-game day). Drives the client-side seasonal foliage
 * tint; vanilla clients never receive it and just see normal colors.
 *
 * @param season       {@link dev.isaac.fallow.api.Season} ordinal
 * @param dayInSeason  0-based day within the season
 * @param daysPerSeason configured season length (for smooth tint transitions)
 * @param enabled      false = seasons disabled server-side; client drops all tinting
 * @param tempOffset   server-authoritative seasonal temperature offset, so the client's
 *                     rain/snow particle rendering matches the server's snow placement exactly
 */
public record SeasonSyncPayload(int season, int dayInSeason, int daysPerSeason, boolean enabled, float tempOffset)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SeasonSyncPayload> ID =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Fallow.MOD_ID, "season"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SeasonSyncPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SeasonSyncPayload::season,
            ByteBufCodecs.VAR_INT, SeasonSyncPayload::dayInSeason,
            ByteBufCodecs.VAR_INT, SeasonSyncPayload::daysPerSeason,
            ByteBufCodecs.BOOL, SeasonSyncPayload::enabled,
            ByteBufCodecs.FLOAT, SeasonSyncPayload::tempOffset,
            SeasonSyncPayload::new
        );

    @Override
    public CustomPacketPayload.Type<SeasonSyncPayload> type() {
        return ID;
    }
}
