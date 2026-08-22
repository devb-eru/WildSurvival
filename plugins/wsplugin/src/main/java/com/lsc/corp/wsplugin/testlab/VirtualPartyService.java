package com.lsc.corp.wsplugin.testlab;

import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class VirtualPartyService implements Listener {
    private final JavaPlugin plugin;
    private final RunService runs;
    private final NamespacedKey dummyIdKey;
    private final NamespacedKey dummyRunKey;
    private final NamespacedKey dummyLifeKey;
    private final Map<UUID, ReviveChannel> channels = new HashMap<>();

    public VirtualPartyService(JavaPlugin plugin, RunService runs) {
        this.plugin = plugin;
        this.runs = runs;
        dummyIdKey = new NamespacedKey(plugin, "test_dummy_id");
        dummyRunKey = new NamespacedKey(plugin, "test_dummy_run");
        dummyLifeKey = new NamespacedKey(plugin, "test_dummy_life");
    }

    public ArmorStand spawn(Player owner, String rawId, String lifeState) {
        requireOwner(owner);
        String id = TestValuePolicy.identifier(rawId).toUpperCase(java.util.Locale.ROOT);
        String life = normalizeLife(lifeState);
        ArmorStand dummy = owner.getWorld().spawn(owner.getLocation().clone().add(
                owner.getLocation().getDirection().setY(0).normalize().multiply(3.0)), ArmorStand.class, stand -> {
            stand.setCustomNameVisible(true);
            stand.setGravity(false);
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.setInvulnerable(true);
            stand.setRemoveWhenFarAway(false);
        });
        PersistentDataContainer pdc = dummy.getPersistentDataContainer();
        pdc.set(dummyIdKey, PersistentDataType.STRING, id);
        pdc.set(dummyRunKey, PersistentDataType.STRING, runs.current().orElseThrow().runId);
        setLife(dummy, life);
        return dummy;
    }

    public void setLife(ArmorStand dummy, String lifeState) {
        String life = normalizeLife(lifeState);
        dummy.getPersistentDataContainer().set(dummyLifeKey, PersistentDataType.STRING, life);
        dummy.setGlowing("DOWNED".equals(life));
        dummy.setVisible(!"DEAD".equals(life));
        dummy.setCustomName(switch (life) {
            case "DOWNED" -> ChatColor.RED + "[가상 빈사] " + dummyId(dummy) + " — 웅크리고 우클릭";
            case "DEAD" -> ChatColor.DARK_GRAY + "[가상 사망] " + dummyId(dummy);
            default -> ChatColor.GREEN + "[가상 파티] " + dummyId(dummy);
        });
    }

    public List<DummyView> list() {
        RunSnapshot run = runs.current().orElse(null);
        if (run == null || !"TEST".equals(run.runType)) {
            return List.of();
        }
        List<DummyView> result = new ArrayList<>();
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (run.runId.equals(stand.getPersistentDataContainer().get(dummyRunKey, PersistentDataType.STRING))) {
                    result.add(new DummyView(stand.getUniqueId().toString(), dummyId(stand), life(stand),
                            world.getName(), stand.getLocation().getX(), stand.getLocation().getY(), stand.getLocation().getZ()));
                }
            }
        }
        return List.copyOf(result);
    }

    public int clear() {
        int removed = 0;
        RunSnapshot run = runs.current().orElse(null);
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity instanceof ArmorStand stand && stand.getPersistentDataContainer().has(dummyRunKey, PersistentDataType.STRING)
                        && (run == null || run.runId.equals(stand.getPersistentDataContainer().get(dummyRunKey, PersistentDataType.STRING)))) {
                    cancelChannel(stand.getUniqueId());
                    stand.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    public int clearOwned(Player owner) {
        requireOwner(owner);
        return clear();
    }

    public void cleanupOrphans() {
        String activeRun = runs.current().filter(run -> "TEST".equals(run.runType) && "RUNNING".equals(run.state))
                .map(run -> run.runId).orElse(null);
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                String runId = stand.getPersistentDataContainer().get(dummyRunKey, PersistentDataType.STRING);
                if (runId != null && !runId.equals(activeRun)) {
                    stand.remove();
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof ArmorStand dummy)
                || !isActiveDummy(dummy) || !"DOWNED".equals(life(dummy))) {
            return;
        }
        Player player = event.getPlayer();
        if (!runs.isRunningMember(player) || !runs.isTestRun()) {
            return;
        }
        event.setCancelled(true);
        if (!player.isSneaking()) {
            player.sendActionBar(Component.text("웅크린 상태로 가상 빈사 대상을 우클릭하세요", NamedTextColor.YELLOW));
            return;
        }
        cancelChannel(dummy.getUniqueId());
        long requiredTicks = Math.max(20L, plugin.getConfig().getLong("test-lab.virtual-revive-ticks", 60L));
        long completesAt = Bukkit.getCurrentTick() + requiredTicks;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!dummy.isValid() || !player.isOnline() || !player.isSneaking()
                    || player.getLocation().distanceSquared(dummy.getLocation()) > 9.0) {
                cancelChannel(dummy.getUniqueId());
                player.sendActionBar(Component.text("가상 구조 취소", NamedTextColor.RED));
                return;
            }
            long remaining = completesAt - Bukkit.getCurrentTick();
            if (remaining <= 0L) {
                setLife(dummy, "ACTIVE");
                cancelChannel(dummy.getUniqueId());
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.7f, 1.3f);
                player.sendMessage(ChatColor.GREEN + "가상 파티원 구조 성공: " + dummyId(dummy));
            } else {
                player.sendActionBar(Component.text("가상 구조 " + Math.ceil(remaining / 20.0) + "초", NamedTextColor.AQUA));
            }
        }, 1L, 5L);
        channels.put(dummy.getUniqueId(), new ReviveChannel(player.getUniqueId(), completesAt, task));
    }

    private void requireOwner(Player player) {
        RunSnapshot run = runs.current().orElseThrow(() -> new IllegalStateException("An active Test Lab run is required"));
        if (!"TEST".equals(run.runType) || run.test == null || !"RUNNING".equals(run.state)
                || !player.getUniqueId().toString().equals(run.test.ownerUuid)) {
            throw new IllegalStateException("Only the Test Lab owner can manage virtual party dummies");
        }
    }

    private boolean isActiveDummy(ArmorStand stand) {
        RunSnapshot run = runs.current().orElse(null);
        return run != null && "TEST".equals(run.runType)
                && run.runId.equals(stand.getPersistentDataContainer().get(dummyRunKey, PersistentDataType.STRING));
    }

    private String dummyId(ArmorStand stand) {
        return stand.getPersistentDataContainer().getOrDefault(dummyIdKey, PersistentDataType.STRING, "DUMMY");
    }

    private String life(ArmorStand stand) {
        return stand.getPersistentDataContainer().getOrDefault(dummyLifeKey, PersistentDataType.STRING, "ACTIVE");
    }

    private static String normalizeLife(String raw) {
        String life = raw.toUpperCase(java.util.Locale.ROOT);
        if (!List.of("ACTIVE", "DOWNED", "DEAD").contains(life)) {
            throw new IllegalArgumentException("Dummy life state must be ACTIVE, DOWNED, or DEAD");
        }
        return life;
    }

    private void cancelChannel(UUID dummyId) {
        ReviveChannel channel = channels.remove(dummyId);
        if (channel != null) {
            channel.task.cancel();
        }
    }

    private record ReviveChannel(UUID playerId, long completesAtTick, BukkitTask task) {
    }

    public record DummyView(String entityUuid, String id, String lifeState, String world,
                            double x, double y, double z) {
    }
}
