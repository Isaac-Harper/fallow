package dev.isaac.fallow.client;

import dev.isaac.fallow.growth.BiomeTuning;
import dev.isaac.fallow.network.SeasonSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client entrypoint: receives season syncs for the visual tint, watches dimension changes
 * (seasonal colors are overworld-only), clears on disconnect.
 */
public final class FallowClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SeasonSyncPayload.ID,
            (payload, context) -> FallowClientSeasons.onSync(payload));
        ClientTickEvents.END_CLIENT_TICK.register(FallowClientSeasons::tickDimensionWatch);
        // Resolve per-biome tuning from the synced registry so the temperature mixin's client-side
        // particle rendering matches the server (same biomeSeasonality scaling).
        ClientPlayConnectionEvents.JOIN.register(
            (handler, sender, client) -> BiomeTuning.rebuild(handler.registryAccess()));
        ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> FallowClientSeasons.clear());
    }
}
