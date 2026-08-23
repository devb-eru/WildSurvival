package com.lsc.corp.wsplugin.ui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** Keeps persistent combat HUD updates from erasing short-lived gameplay feedback. */
public final class ActionBarService {
    private static final Map<UUID, Notice> NOTICES = new ConcurrentHashMap<>();

    private ActionBarService() {
    }

    public static void notice(Player player, Component message, int durationTicks) {
        show(player, message, durationTicks, 20);
    }

    public static void important(Player player, Component message, int durationTicks) {
        show(player, message, durationTicks, 50);
    }

    public static void critical(Player player, Component message, int durationTicks) {
        show(player, message, durationTicks, 100);
    }

    public static void show(Player player, Component message, int durationTicks, int priority) {
        long now = System.nanoTime();
        Notice active = NOTICES.get(player.getUniqueId());
        if (active != null && active.expiresAtNanos > now && active.priority > priority) {
            return;
        }
        NOTICES.put(player.getUniqueId(), new Notice(now + Math.max(1, durationTicks) * 50_000_000L, priority));
        player.sendActionBar(message);
    }

    public static void renderHud(Player player, Component hud) {
        long now = System.nanoTime();
        Notice active = NOTICES.get(player.getUniqueId());
        if (active != null && active.expiresAtNanos > now) {
            return;
        }
        if (active != null) {
            NOTICES.remove(player.getUniqueId(), active);
        }
        player.sendActionBar(hud);
    }

    public static void clear(Player player) {
        NOTICES.remove(player.getUniqueId());
    }

    private record Notice(long expiresAtNanos, int priority) {
    }
}
