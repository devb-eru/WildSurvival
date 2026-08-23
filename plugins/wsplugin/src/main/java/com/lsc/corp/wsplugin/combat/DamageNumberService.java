package com.lsc.corp.wsplugin.combat;

import com.lsc.corp.wsplugin.run.RunService;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class DamageNumberService implements Listener {
    private static final int MAX_ACTIVE = 160;
    private static final int MAX_PER_VICTIM = 8;
    private static final int MAX_PER_VIEWER_PER_SECOND = 40;
    private static final DecimalFormat FORMAT = new DecimalFormat("0.#");
    private final JavaPlugin plugin;
    private final RunService runs;
    private final Set<UUID> active = new HashSet<>();
    private final Map<DamageKey, PendingDamage> pending = new HashMap<>();
    private final Map<UUID, UUID> victimByDisplay = new HashMap<>();
    private final Map<UUID, ViewerWindow> viewerWindows = new HashMap<>();

    public DamageNumberService(JavaPlugin plugin, RunService runs) {
        this.plugin = plugin;
        this.runs = runs;
    }

    public void show(Player source, LivingEntity victim, double damage) {
        if (damage <= 0.0 || !victim.isValid()) return;
        DamageKey key = new DamageKey(source.getUniqueId(), victim.getUniqueId());
        PendingDamage aggregate = pending.computeIfAbsent(key, ignored -> new PendingDamage(source, victim));
        aggregate.damage += damage;
        aggregate.hits++;
        if (aggregate.scheduled) return;
        aggregate.scheduled = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> flush(key), 4L);
    }

    private void flush(DamageKey key) {
        PendingDamage aggregate = pending.remove(key);
        if (aggregate == null || aggregate.damage <= 0.0 || !aggregate.victim.isValid() || active.size() >= MAX_ACTIVE) return;
        long victimDisplays = victimByDisplay.values().stream().filter(aggregate.victim.getUniqueId()::equals).count();
        if (victimDisplays >= MAX_PER_VICTIM) return;
        LivingEntity victim = aggregate.victim;
        Location location = victim.getLocation().add((Math.random() - 0.5) * 0.5,
                Math.max(1.0, victim.getHeight() * 0.75), (Math.random() - 0.5) * 0.5);
        String suffix = aggregate.hits > 1 ? " ×" + aggregate.hits : "";
        TextDisplay display = victim.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.text(Component.text("-" + FORMAT.format(aggregate.damage) + suffix, NamedTextColor.RED));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(true);
            entity.setShadowed(true);
            entity.setGravity(false);
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
        });
        active.add(display.getUniqueId());
        victimByDisplay.put(display.getUniqueId(), victim.getUniqueId());
        long now = System.currentTimeMillis();
        for (Player viewer : runs.onlineMembers()) {
            boolean enabled = runs.playerState(viewer.getUniqueId()).map(state -> state.damageNumbersEnabled).orElse(true);
            if (enabled && viewer.getWorld().equals(victim.getWorld())
                    && viewer.getLocation().distanceSquared(victim.getLocation()) <= 1024.0
                    && allowViewer(viewer.getUniqueId(), now)) {
                viewer.showEntity(plugin, display);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) display.teleport(display.getLocation().add(0.0, 0.35, 0.0));
        }, 6L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            active.remove(display.getUniqueId());
            victimByDisplay.remove(display.getUniqueId());
            if (display.isValid()) display.remove();
        }, 14L);
    }

    private boolean allowViewer(UUID viewerId, long now) {
        ViewerWindow window = viewerWindows.computeIfAbsent(viewerId, ignored -> new ViewerWindow(now));
        if (now - window.startedAt >= 1_000L) {
            window.startedAt = now;
            window.count = 0;
        }
        if (window.count >= MAX_PER_VIEWER_PER_SECOND) return false;
        window.count++;
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanillaDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || event.getFinalDamage() <= 0.0) return;
        Player source = event.getDamager() instanceof Player player ? player : null;
        if (source != null && runs.isRunningMember(source)) show(source, victim, event.getFinalDamage());
    }

    public void cleanup() {
        for (UUID id : Set.copyOf(active)) {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                org.bukkit.entity.Entity entity = world.getEntity(id);
                if (entity != null) entity.remove();
            }
        }
        active.clear();
        pending.clear();
        victimByDisplay.clear();
        viewerWindows.clear();
    }

    private record DamageKey(UUID source, UUID victim) { }
    private static final class PendingDamage {
        private final Player source;
        private final LivingEntity victim;
        private double damage;
        private int hits;
        private boolean scheduled;
        private PendingDamage(Player source, LivingEntity victim) { this.source = source; this.victim = victim; }
    }
    private static final class ViewerWindow {
        private long startedAt;
        private int count;
        private ViewerWindow(long startedAt) { this.startedAt = startedAt; }
    }
}
