package com.lsc.corp.wsplugin.combat;

import com.lsc.corp.wsplugin.run.RunService;
import java.text.DecimalFormat;
import java.util.HashSet;
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
    private static final int MAX_ACTIVE = 48;
    private static final DecimalFormat FORMAT = new DecimalFormat("0.#");
    private final JavaPlugin plugin;
    private final RunService runs;
    private final Set<UUID> active = new HashSet<>();

    public DamageNumberService(JavaPlugin plugin, RunService runs) {
        this.plugin = plugin;
        this.runs = runs;
    }

    public void show(Player source, LivingEntity victim, double damage) {
        if (damage <= 0.0 || !victim.isValid() || active.size() >= MAX_ACTIVE) return;
        Location location = victim.getLocation().add((Math.random() - 0.5) * 0.5,
                Math.max(1.0, victim.getHeight() * 0.75), (Math.random() - 0.5) * 0.5);
        TextDisplay display = victim.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.text(Component.text("-" + FORMAT.format(damage), NamedTextColor.RED));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(true);
            entity.setShadowed(true);
            entity.setGravity(false);
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
        });
        active.add(display.getUniqueId());
        for (Player viewer : runs.onlineMembers()) {
            boolean enabled = runs.playerState(viewer.getUniqueId()).map(state -> state.damageNumbersEnabled).orElse(true);
            if (enabled && viewer.getWorld().equals(victim.getWorld())
                    && viewer.getLocation().distanceSquared(victim.getLocation()) <= 4096.0) {
                viewer.showEntity(plugin, display);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) display.teleport(display.getLocation().add(0.0, 0.35, 0.0));
        }, 6L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            active.remove(display.getUniqueId());
            if (display.isValid()) display.remove();
        }, 20L);
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
    }
}
