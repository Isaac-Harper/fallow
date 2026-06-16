package dev.isaac.fallow.notice;

import dev.isaac.fallow.Fallow;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * One-time, per-player in-game notice that Fallow alters blocks over time and is therefore opt-in.
 * Shown the first time a player joins a world that has Fallow installed (tracked persistently in
 * {@link NoticeData}), whether or not the mod is currently enabled, so nobody is surprised by a
 * world that slowly edits itself. Plain literal text, so it shows correctly on vanilla clients too.
 */
public final class FirstJoinNotice {
    private FirstJoinNotice() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            ServerLevel overworld = server.overworld();
            NoticeData data = NoticeData.get(overworld);
            if (data.warned.add(player.getUUID())) {
                data.setDirty();
                for (Component line : message()) {
                    player.sendSystemMessage(line);
                }
            }
        });
    }

    private static Component[] message() {
        Component head = Component.literal(
            "[Fallow] This world has the Fallow mod, which slowly changes blocks over time: grass and "
            + "flowers spread, dirt paths wear in underfoot, trees and bamboo spread, snow piles up and "
            + "melts, and plants left in the dark decay. These edits are permanent and can be considered "
            + "destructive.").withStyle(ChatFormatting.GOLD);
        Component state = Fallow.CONFIG.enabled
            ? Component.literal(
                "[Fallow] It is currently ENABLED. To turn it off, set \"enabled\": false in "
                + "config/fallow.json and run /fallow reload.").withStyle(ChatFormatting.YELLOW)
            : Component.literal(
                "[Fallow] It is DISABLED by default. Back up your world first, then set \"enabled\": true "
                + "in config/fallow.json and run /fallow reload to turn it on.").withStyle(ChatFormatting.YELLOW);
        return new Component[] { head, state };
    }
}
