package com.yaseen.aicore.chat;

import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.event.ServerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Routes the owner's normal chat directly into the companion conversation. */
public final class ChatBridge {
    private static final java.util.Map<java.util.UUID, Long> LAST_NOTICE = new java.util.concurrent.ConcurrentHashMap<>();
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String raw = event.getMessage().getString();
        String message = raw == null ? "" : raw.trim();
        if (message.isBlank() || message.startsWith("/")) return;

        CompanionEntity companion = ServerEvents.findOwnedCompanion(player.server, player.getUUID());
        if (companion == null) {
            com.yaseen.aicore.AICompanion.LOG.warn("[AICore] chat ignored: no loaded companion owned by {}", player.getGameProfile().getName());
            long now = System.currentTimeMillis();
            Long last = LAST_NOTICE.get(player.getUUID());
            if (last == null || now - last > 15_000L) {
                LAST_NOTICE.put(player.getUUID(), now);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[AI] رفيقك غير محمّل الآن ❌ — اكتب /aic respawn أو /aic diag"));
            }
            return;
        }
        if (!player.getUUID().equals(companion.getOwner())) return;

        // Forward every normal owner message to the companion. Do NOT cancel the
        // ServerChatEvent: cancelling makes the player's own message disappear
        // from Minecraft chat and can interfere with other chat listeners.
        // The companion reply is sent separately by AgentEngine.
        try {
            com.yaseen.aicore.AICompanion.LOG.debug("[AICore] chat -> companion: {}", message);
            companion.ask(message);
        } catch (Throwable t) {
            com.yaseen.aicore.AICompanion.LOG.error("[AICore] companion.ask failed", t);
            companion.say("صار خطأ داخلي: " + t.getClass().getSimpleName());
        }
    }
}
