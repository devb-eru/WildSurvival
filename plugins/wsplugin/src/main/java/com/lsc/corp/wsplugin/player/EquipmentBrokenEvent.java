package com.lsc.corp.wsplugin.player;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a WildSurvival equipment instance reaches zero server-authoritative durability.
 * The backing ItemStack remains equipped in the BROKEN condition and is never natively deleted.
 */
public final class EquipmentBrokenEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String templateId;
    private final String instanceId;
    private final String reason;

    public EquipmentBrokenEvent(Player player, String templateId, String instanceId, String reason) {
        this.player = Objects.requireNonNull(player, "player");
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Player getPlayer() {
        return player;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
