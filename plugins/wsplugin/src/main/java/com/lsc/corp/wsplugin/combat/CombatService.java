package com.lsc.corp.wsplugin.combat;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.EquipmentService;
import com.lsc.corp.wsplugin.player.PlayerStatPolicy;
import com.lsc.corp.wsplugin.player.SkillLoadoutService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import com.lsc.corp.wsplugin.ui.ActionBarService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class CombatService implements Listener {
    private static final double PLAYER_HP_SCALE = 5.0;
    private static final double REVIVE_RANGE_SQUARED = 2.5 * 2.5;
    private final JavaPlugin plugin;
    private final RunService runs;
    private final PrototypeContent content;
    private final EquipmentService equipment;
    private final GrowthService growth;
    private final SkillLoadoutService skills;
    private final TelemetryService telemetry;
    private final NamespacedKey enemyIdKey;
    private final NamespacedKey enemyRunIdKey;
    private final NamespacedKey customHpKey;
    private final NamespacedKey customMaxHpKey;
    private final NamespacedKey defenceKey;
    private final NamespacedKey breakKey;
    private final NamespacedKey breakMaxKey;
    private final NamespacedKey groggyUntilKey;
    private final NamespacedKey attackDamageOverrideKey;
    private final NamespacedKey testInvulnerableKey;
    private final NamespacedKey projectileOwnerKey;
    private final NamespacedKey projectileWeaponKey;
    private final Map<UUID, ComboState> combos = new HashMap<>();
    private final Map<UUID, Long> attackReadyAtNanos = new HashMap<>();
    private final Map<UUID, Long> lastLeftInputTick = new HashMap<>();
    private final Map<UUID, Boolean> lastLeftHit = new HashMap<>();
    private final Map<UUID, Long> invulnerableUntilEpochMs = new HashMap<>();
    private final Map<UUID, Long> lastSneakAtEpochMs = new HashMap<>();
    private final Map<UUID, ReviveChannel> reviveChannels = new HashMap<>();
    private final Map<UUID, BossBar> breakBars = new HashMap<>();
    private final Set<UUID> activeCombatEntities = new HashSet<>();
    private final Map<UUID, Long> tridentHitCooldown = new HashMap<>();
    private BossDamageHandler bossDamageHandler;
    private Consumer<Player> menuOpener = player -> { };
    private ItemRewardHandler itemRewardHandler = (player, resourceId, amount) -> { };
    private DamageNumberService damageNumbers;
    private boolean scannedPersistedEntities;
    private int hudTick;

    public CombatService(JavaPlugin plugin, RunService runs, PrototypeContent content, EquipmentService equipment,
                         GrowthService growth, SkillLoadoutService skills, TelemetryService telemetry) {
        this.plugin = plugin;
        this.runs = runs;
        this.content = content;
        this.equipment = equipment;
        this.growth = growth;
        this.skills = skills;
        this.telemetry = telemetry;
        this.enemyIdKey = new NamespacedKey(plugin, "enemy_id");
        this.enemyRunIdKey = new NamespacedKey(plugin, "enemy_run_id");
        this.customHpKey = new NamespacedKey(plugin, "custom_hp");
        this.customMaxHpKey = new NamespacedKey(plugin, "custom_max_hp");
        this.defenceKey = new NamespacedKey(plugin, "defence");
        this.breakKey = new NamespacedKey(plugin, "break_current");
        this.breakMaxKey = new NamespacedKey(plugin, "break_max");
        this.groggyUntilKey = new NamespacedKey(plugin, "groggy_until");
        this.attackDamageOverrideKey = new NamespacedKey(plugin, "test_attack_damage");
        this.testInvulnerableKey = new NamespacedKey(plugin, "test_invulnerable");
        this.projectileOwnerKey = new NamespacedKey(plugin, "projectile_owner");
        this.projectileWeaponKey = new NamespacedKey(plugin, "projectile_weapon");
    }

    public void setBossDamageHandler(BossDamageHandler bossDamageHandler) {
        this.bossDamageHandler = bossDamageHandler;
    }

    public void setMenuOpener(Consumer<Player> menuOpener) {
        this.menuOpener = menuOpener;
    }

    public void setItemRewardHandler(ItemRewardHandler itemRewardHandler) {
        this.itemRewardHandler = itemRewardHandler;
    }

    public void setDamageNumbers(DamageNumberService damageNumbers) {
        this.damageNumbers = damageNumbers;
    }

    public LivingEntity spawnEnemy(PrototypeContent.EnemyDefinition definition, Location location) {
        EntityType type;
        try {
            type = EntityType.valueOf(definition.entityType());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid living entity type " + definition.entityType());
        }
        if (!type.isAlive()) {
            throw new IllegalArgumentException("Entity type is not living " + definition.entityType());
        }
        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, type);
        tagCombatEntity(entity, definition.id(), definition.hp(), definition.defence(), definition.breakMax());
        entity.setCustomName(ChatColor.RED + definition.name() + ChatColor.GRAY + " [" + definition.role() + "]");
        entity.setCustomNameVisible(true);
        var maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(Math.max(1.0, Math.min(maxHealth.getBaseValue(), 40.0)));
            entity.setHealth(maxHealth.getBaseValue());
        }
        return entity;
    }

    public void tagCombatEntity(LivingEntity entity, String id, double hp, double defence, double breakMax) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(enemyIdKey, PersistentDataType.STRING, id);
        pdc.set(enemyRunIdKey, PersistentDataType.STRING, runs.current().orElseThrow().runId);
        pdc.set(customHpKey, PersistentDataType.DOUBLE, hp);
        pdc.set(customMaxHpKey, PersistentDataType.DOUBLE, hp);
        pdc.set(defenceKey, PersistentDataType.DOUBLE, defence);
        pdc.set(breakKey, PersistentDataType.DOUBLE, 0.0);
        pdc.set(breakMaxKey, PersistentDataType.DOUBLE, breakMax);
        activeCombatEntities.add(entity.getUniqueId());
    }

    public void tick() {
        long now = Instant.now().toEpochMilli();
        processRevives(now);
        processDownedTimeouts(now);
        processTridents(now);
        if (++hudTick % 10 == 0) {
            updateHud();
            refreshBreakBars();
        }
    }

    public boolean hasActiveEnemies() {
        if (!scannedPersistedEntities) {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                world.getLivingEntities().stream().filter(this::isCombatEntity).map(Entity::getUniqueId).forEach(activeCombatEntities::add);
            }
            scannedPersistedEntities = true;
        }
        activeCombatEntities.removeIf(uuid -> findEntity(uuid.toString()) == null);
        return !activeCombatEntities.isEmpty();
    }

    public void cleanupForeignCombatEntities() {
        String currentRunId = runs.current().map(run -> run.runId).orElse("");
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                PersistentDataContainer pdc = entity.getPersistentDataContainer();
                String taggedRun = pdc.get(enemyRunIdKey, PersistentDataType.STRING);
                if (pdc.has(enemyIdKey, PersistentDataType.STRING) && taggedRun != null && !taggedRun.equals(currentRunId)) {
                    entity.remove();
                }
            }
        }
    }

    public void cleanupCombatEntities() {
        for (UUID uuid : new HashSet<>(activeCombatEntities)) {
            Entity entity = findEntity(uuid.toString());
            if (entity != null) {
                entity.remove();
            }
        }
        activeCombatEntities.clear();
        breakBars.values().forEach(BossBar::removeAll);
        breakBars.clear();
    }

    public double damageCombatEntity(Player attacker, LivingEntity target, double rawAttack, double breakDamage, String executionId) {
        if (!isCombatEntity(target)) {
            return 0.0;
        }
        String id = enemyId(target);
        PersistentDataContainer pdc = target.getPersistentDataContainer();
        double defence = pdc.getOrDefault(defenceKey, PersistentDataType.DOUBLE, 0.0);
        long groggyUntil = pdc.getOrDefault(groggyUntilKey, PersistentDataType.LONG, 0L);
        double groggyMultiplier = groggyUntil > Instant.now().toEpochMilli() ? 1.15 : 1.0;
        RunSnapshot.PlayerState attackerState = runs.playerState(attacker.getUniqueId()).orElse(null);
        double testDamageMultiplier = runs.isTestRun() && attackerState != null
                ? clamp(attackerState.testDamageDealtMultiplier, 0.0, 100.0) : 1.0;
        double testBreakMultiplier = runs.isTestRun() && attackerState != null
                ? clamp(attackerState.testBreakMultiplier, 0.0, 100.0) : 1.0;
        double finalDamage = CombatMath.outgoingDamage(rawAttack, Math.max(0.0, defence),
                growth.attackMultiplier(attacker), groggyMultiplier, testDamageMultiplier);
        double finalBreak = Math.max(0.0, breakDamage * growth.breakMultiplier(attacker) * testBreakMultiplier);
        if (pdc.getOrDefault(testInvulnerableKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1) {
            finalDamage = 0.0;
            finalBreak = 0.0;
        }
        if (id.startsWith("BOSS-") && bossDamageHandler != null) {
            bossDamageHandler.damage(attacker, target, finalDamage, finalBreak, executionId);
            if (damageNumbers != null) damageNumbers.show(attacker, target, finalDamage);
            return finalDamage;
        }
        double hp = pdc.getOrDefault(customHpKey, PersistentDataType.DOUBLE, 1.0) - finalDamage;
        pdc.set(customHpKey, PersistentDataType.DOUBLE, Math.max(0.0, hp));
        applyBreak(target, finalBreak);
        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0.0);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.35f, 1.2f);
        if (damageNumbers != null) damageNumbers.show(attacker, target, finalDamage);
        if (hp <= 0.0) {
            defeatEnemy(attacker, target, id);
        }
        telemetry.event(runs.current().orElseThrow().runId, "COMBAT_RESULT",
                "{\"executionId\":\"" + executionId + "\",\"targetId\":\"" + id + "\",\"damage\":" + round(finalDamage) + ",\"break\":" + round(finalBreak) + "}");
        return finalDamage;
    }

    public void applyBreak(LivingEntity target, double amount) {
        PersistentDataContainer pdc = target.getPersistentDataContainer();
        double max = pdc.getOrDefault(breakMaxKey, PersistentDataType.DOUBLE, 0.0);
        if (max <= 0.0 || amount <= 0.0) {
            return;
        }
        double current = Math.min(max, pdc.getOrDefault(breakKey, PersistentDataType.DOUBLE, 0.0) + amount);
        pdc.set(breakKey, PersistentDataType.DOUBLE, current);
        updateBreakBar(target, current, max);
        if (current >= max) {
            pdc.set(breakKey, PersistentDataType.DOUBLE, 0.0);
            pdc.set(groggyUntilKey, PersistentDataType.LONG, Instant.now().plusMillis(5000).toEpochMilli());
            target.setAI(false);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (target.isValid() && !target.isDead()) {
                    target.setAI(true);
                }
            }, 100L);
            runs.broadcast(ChatColor.GOLD + "[BREAK] " + (target.getCustomName() == null ? "대상" : target.getCustomName()) + " 그로기 5초");
            runs.mutate(run -> run.players.values().forEach(state -> state.ap = Math.min(state.maxAp, state.ap + state.maxAp * 0.20)));
        }
    }

    public double currentBreak(LivingEntity target) {
        return target.getPersistentDataContainer().getOrDefault(breakKey, PersistentDataType.DOUBLE, 0.0);
    }

    public java.util.Optional<LivingEntity> targetedCombatEntity(Player player, double range) {
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                player.getEyeLocation().getDirection(), range, 0.5,
                entity -> entity instanceof LivingEntity living && isCombatEntity(living));
        return result != null && result.getHitEntity() instanceof LivingEntity living
                ? java.util.Optional.of(living) : java.util.Optional.empty();
    }

    public boolean isManagedCombatEntity(Entity entity) {
        return isCombatEntity(entity);
    }

    public CombatEntityView inspectCombatEntity(LivingEntity target) {
        if (!isCombatEntity(target)) {
            throw new IllegalArgumentException("Target is not a WildSurvival combat entity");
        }
        PersistentDataContainer pdc = target.getPersistentDataContainer();
        List<String> statuses = pdc.getKeys().stream()
                .filter(key -> key.getKey().startsWith("status_") || key.getKey().startsWith("test_status_"))
                .map(NamespacedKey::asString).sorted().toList();
        return new CombatEntityView(target.getUniqueId().toString(), enemyId(target), target.getType().name(),
                pdc.getOrDefault(customHpKey, PersistentDataType.DOUBLE, 0.0),
                pdc.getOrDefault(customMaxHpKey, PersistentDataType.DOUBLE, 0.0),
                pdc.getOrDefault(defenceKey, PersistentDataType.DOUBLE, 0.0),
                pdc.getOrDefault(breakKey, PersistentDataType.DOUBLE, 0.0),
                pdc.getOrDefault(breakMaxKey, PersistentDataType.DOUBLE, 0.0),
                enemyAttackDamage(target), target.hasAI(),
                pdc.getOrDefault(testInvulnerableKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1,
                statuses);
    }

    public void setCombatEntityNumber(LivingEntity target, String stat, double value) {
        if (!isCombatEntity(target) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("A managed target and finite value are required");
        }
        PersistentDataContainer pdc = target.getPersistentDataContainer();
        switch (stat.toLowerCase(java.util.Locale.ROOT).replace('_', '-')) {
            case "health", "hp" -> {
                double maximum = pdc.getOrDefault(customMaxHpKey, PersistentDataType.DOUBLE, 1.0);
                pdc.set(customHpKey, PersistentDataType.DOUBLE, clamp(value, 0.0, maximum));
                syncBossNumber(target, "health", clamp(value, 0.0, maximum));
            }
            case "max-health", "max-hp" -> {
                double maximum = clamp(value, 1.0, 100_000_000.0);
                pdc.set(customMaxHpKey, PersistentDataType.DOUBLE, maximum);
                pdc.set(customHpKey, PersistentDataType.DOUBLE,
                        Math.min(maximum, pdc.getOrDefault(customHpKey, PersistentDataType.DOUBLE, maximum)));
                syncBossNumber(target, "max-health", maximum);
            }
            case "defence", "defense" -> pdc.set(defenceKey, PersistentDataType.DOUBLE, clamp(value, 0.0, 100_000.0));
            case "break" -> pdc.set(breakKey, PersistentDataType.DOUBLE,
                    clamp(value, 0.0, pdc.getOrDefault(breakMaxKey, PersistentDataType.DOUBLE, 0.0)));
            case "break-max" -> {
                double maximum = clamp(value, 0.0, 100_000_000.0);
                pdc.set(breakMaxKey, PersistentDataType.DOUBLE, maximum);
                pdc.set(breakKey, PersistentDataType.DOUBLE,
                        Math.min(maximum, pdc.getOrDefault(breakKey, PersistentDataType.DOUBLE, 0.0)));
            }
            case "attack", "attack-damage" -> pdc.set(attackDamageOverrideKey, PersistentDataType.DOUBLE,
                    clamp(value, 0.0, 100_000.0));
            default -> throw new IllegalArgumentException("Unknown entity stat " + stat);
        }
    }

    public void setCombatEntityFlag(LivingEntity target, String flag, boolean value) {
        if (!isCombatEntity(target)) {
            throw new IllegalArgumentException("Target is not a WildSurvival combat entity");
        }
        switch (flag.toLowerCase(java.util.Locale.ROOT).replace('_', '-')) {
            case "ai" -> target.setAI(value);
            case "invulnerable" -> target.getPersistentDataContainer().set(testInvulnerableKey,
                    PersistentDataType.BYTE, value ? (byte) 1 : (byte) 0);
            case "glowing" -> target.setGlowing(value);
            default -> throw new IllegalArgumentException("Unknown entity flag " + flag);
        }
    }

    public void applyTestStatus(LivingEntity target, String statusId, int durationTicks, int amplifier) {
        if (!isCombatEntity(target)) {
            throw new IllegalArgumentException("Target is not a WildSurvival combat entity");
        }
        if (durationTicks < 1 || durationTicks > 1_728_000 || amplifier < 0 || amplifier > 255) {
            throw new IllegalArgumentException("Status duration or amplifier is outside the test boundary");
        }
        String id = statusId.toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        NamespacedKey key = new NamespacedKey(plugin, "test_status_" + id.toLowerCase(java.util.Locale.ROOT));
        target.getPersistentDataContainer().set(key, PersistentDataType.LONG,
                Instant.now().plusMillis(durationTicks * 50L).toEpochMilli());
        switch (id) {
            case "MARK" -> target.setGlowing(true);
            case "SLOW", "SLOWNESS" -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    durationTicks, amplifier, true, true));
            case "WEAKNESS" -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                    durationTicks, amplifier, true, true));
            case "POISON" -> target.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                    durationTicks, amplifier, true, true));
            case "GLOWING" -> target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                    durationTicks, amplifier, true, true));
            case "GROGGY" -> {
                target.getPersistentDataContainer().set(groggyUntilKey, PersistentDataType.LONG,
                        Instant.now().plusMillis(durationTicks * 50L).toEpochMilli());
                target.setAI(false);
            }
            default -> {
                PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(id.toLowerCase(java.util.Locale.ROOT)));
                if (type != null) {
                    target.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, true, true));
                }
            }
        }
    }

    public void clearTestStatuses(LivingEntity target) {
        if (!isCombatEntity(target)) {
            throw new IllegalArgumentException("Target is not a WildSurvival combat entity");
        }
        List<NamespacedKey> keys = new ArrayList<>(target.getPersistentDataContainer().getKeys());
        keys.stream().filter(key -> key.getKey().startsWith("status_") || key.getKey().startsWith("test_status_"))
                .forEach(target.getPersistentDataContainer()::remove);
        target.getPersistentDataContainer().remove(groggyUntilKey);
        target.getActivePotionEffects().forEach(effect -> target.removePotionEffect(effect.getType()));
        target.setGlowing(false);
        target.setAI(true);
    }

    public DamagePreview previewDamage(Player attacker, LivingEntity target, double rawDamage, double rawBreak) {
        CombatEntityView view = inspectCombatEntity(target);
        RunSnapshot.PlayerState state = runs.playerState(attacker.getUniqueId()).orElseThrow();
        double defenceFactor = 100.0 / (100.0 + Math.max(0.0, view.defence()));
        double augmentDamage = growth.attackMultiplier(attacker);
        double augmentBreak = growth.breakMultiplier(attacker);
        double testDamage = runs.isTestRun() ? clamp(state.testDamageDealtMultiplier, 0.0, 100.0) : 1.0;
        double testBreak = runs.isTestRun() ? clamp(state.testBreakMultiplier, 0.0, 100.0) : 1.0;
        return new DamagePreview(rawDamage, defenceFactor, augmentDamage, testDamage,
                Math.max(0.0, rawDamage * defenceFactor * augmentDamage * testDamage), rawBreak,
                augmentBreak, testBreak, Math.max(0.0, rawBreak * augmentBreak * testBreak));
    }

    private void syncBossNumber(LivingEntity target, String stat, double value) {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || snapshot.boss == null
                || !target.getUniqueId().toString().equals(snapshot.boss.entityUuid)) {
            return;
        }
        runs.mutate(run -> {
            if ("health".equals(stat)) {
                run.boss.hp = value;
            } else if ("max-health".equals(stat)) {
                run.boss.maxHp = value;
                run.boss.hp = Math.min(run.boss.hp, value);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVanillaPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && runs.isRunningMember(player)) {
            event.setCancelled(true);
        }
        if (event.getDamager() instanceof Arrow arrow && arrow.getPersistentDataContainer().has(projectileOwnerKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
        if (event.getDamager() instanceof Trident trident && trident.getPersistentDataContainer().has(projectileOwnerKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker
                && runs.isMember(victim) && runs.isMember(attacker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !runs.isRunningMember(player)) {
            return;
        }
        RunSnapshot.PlayerState playerState = runs.playerState(player.getUniqueId()).orElse(null);
        if (playerState == null) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            LivingEntity attacker = combatAttacker(byEntity);
            if (attacker != null) {
                event.setDamage(enemyAttackDamage(attacker) / PLAYER_HP_SCALE);
            }
        }
        if (runs.isTestRun() && playerState != null) {
            if (playerState.testInvulnerable) {
                event.setCancelled(true);
                return;
            }
            double multiplier = clamp(playerState.testDamageTakenMultiplier, 0.0, 100.0);
            double reduction = clamp(playerState.testDamageReductionRate, 0.0, 0.95);
            event.setDamage(CombatMath.incomingDamage(event.getDamage(), multiplier, reduction));
        }
        event.setDamage(event.getDamage() * PlayerStatPolicy.incomingDamageMultiplier(playerState.investedStats));
        long now = Instant.now().toEpochMilli();
        if (invulnerableUntilEpochMs.getOrDefault(player.getUniqueId(), 0L) >= now) {
            event.setCancelled(true);
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 8, 0.4, 0.2, 0.4, 0.02);
            runs.mutate(run -> {
                RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                state.ap = Math.min(state.maxAp, state.ap + 10.0);
            });
            telemetry.event(runs.current().orElseThrow().runId, "DODGE_SUCCESS", "{\"refund\":10}");
            return;
        }
        switch (PlayerLifePolicy.evaluate(playerState.lifeState, player.getHealth(), event.getFinalDamage())) {
            case BLOCK -> event.setCancelled(true);
            case ENTER_DOWNED -> {
                event.setCancelled(true);
                enterDowned(player);
            }
            case ALLOW -> {
                if (event.getFinalDamage() > 0.0) {
                    runs.mutate(run -> run.players.get(player.getUniqueId().toString()).apRegenBlockedUntilEpochMs = now + 1500L);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBowShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && runs.isRunningMember(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING || !runs.isRunningMember(event.getPlayer())
                || !inCombatStance(event.getPlayer())) {
            return;
        }
        routeLeft(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!runs.isRunningMember(player)) {
            return;
        }
        if (isActionRestricted(player)) {
            event.setCancelled(true);
            showRestrictedAction(player);
            return;
        }
        if (!inCombatStance(player)) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            routeLeft(player);
            if (action == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
            }
            return;
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            executeWeaponActive(player, player.isSneaking() ? 3 : 1);
            returnToCombatStance(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRestrictedBlockBreak(BlockBreakEvent event) {
        if (runs.isRunningMember(event.getPlayer()) && isActionRestricted(event.getPlayer())) {
            event.setCancelled(true);
            showRestrictedAction(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRestrictedBlockPlace(BlockPlaceEvent event) {
        if (runs.isRunningMember(event.getPlayer()) && isActionRestricted(event.getPlayer())) {
            event.setCancelled(true);
            showRestrictedAction(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRestrictedPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && runs.isRunningMember(player) && isActionRestricted(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRestrictedDrop(PlayerDropItemEvent event) {
        if (runs.isRunningMember(event.getPlayer()) && isActionRestricted(event.getPlayer())) {
            event.setCancelled(true);
            showRestrictedAction(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRestrictedInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && runs.isRunningMember(player) && isActionRestricted(player)) {
            event.setCancelled(true);
            showRestrictedAction(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRestrictedInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && runs.isRunningMember(player) && isActionRestricted(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRestrictedInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && runs.isRunningMember(player) && isActionRestricted(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRestrictedSprint(PlayerToggleSprintEvent event) {
        if (event.isSprinting() && runs.isRunningMember(event.getPlayer()) && isActionRestricted(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().setSprinting(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRestrictedJump(PlayerJumpEvent event) {
        if (runs.isRunningMember(event.getPlayer()) && isActionRestricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!runs.isRunningMember(event.getPlayer())) {
            return;
        }
        Player player = event.getPlayer();
        if (isActionRestricted(player)) {
            event.setCancelled(true);
            showRestrictedAction(player);
            return;
        }
        int originalSlot = player.getInventory().getHeldItemSlot();
        if (player.isSneaking()) {
            event.setCancelled(true);
            menuOpener.accept(player);
        } else if (inCombatStance(player)) {
            event.setCancelled(true);
            executeWeaponActive(player, 3);
        } else {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            equipment.syncAuthoritativeEquipment(player);
            player.getInventory().setHeldItemSlot(originalSlot);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHeldSlot(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!runs.isRunningMember(player)) {
            return;
        }
        int slot = event.getNewSlot();
        if (!player.isSneaking() || event.getPreviousSlot() != 0) {
            return;
        }
        if (slot >= 1 && slot <= 4) {
            event.setCancelled(true);
            executeCommonActive(player, slot);
            returnToCombatStance(player);
        } else if (slot >= 5 && slot <= 8) {
            event.setCancelled(true);
            executeQuickItem(player, slot - 4);
            returnToCombatStance(player);
        }
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking() || !runs.isRunningMember(player) || !inCombatStance(player)
                || !requireActiveAction(player)) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        long previous = lastSneakAtEpochMs.getOrDefault(player.getUniqueId(), 0L);
        lastSneakAtEpochMs.put(player.getUniqueId(), now);
        if (now - previous <= 300L) {
            executeDodge(player);
            lastSneakAtEpochMs.put(player.getUniqueId(), 0L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        Entity projectile = event.getEntity();
        PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        String ownerId = pdc.get(projectileOwnerKey, PersistentDataType.STRING);
        String weaponId = pdc.get(projectileWeaponKey, PersistentDataType.STRING);
        if (ownerId == null || weaponId == null) {
            return;
        }
        Player player = Bukkit.getPlayer(UUID.fromString(ownerId));
        if (player == null) {
            return;
        }
        if (event.getHitEntity() instanceof LivingEntity target && isCombatEntity(target)) {
            PrototypeContent.WeaponDefinition weapon = contentWeapon(player, weaponId);
            damageCombatEntity(player, target, 100.0 * weapon.attackCoefficients().get(0), weapon.breakDamage().get(0),
                    "projectile:" + projectile.getUniqueId());
        }
        if (projectile instanceof Trident) {
            storeThrownTrident(player, projectile.getLocation(), projectile.getUniqueId());
            projectile.setVelocity(new Vector());
            projectile.setGravity(false);
        } else {
            projectile.remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVanillaDeath(EntityDeathEvent event) {
        if (isCombatEntity(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler
    public void onReviveInteract(PlayerInteractEntityEvent event) {
        Player reviver = event.getPlayer();
        if (runs.isRunningMember(reviver) && isActionRestricted(reviver)) {
            event.setCancelled(true);
            showRestrictedAction(reviver);
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)
                || !runs.isRunningMember(reviver) || !runs.isRunningMember(target)) {
            return;
        }
        RunSnapshot.PlayerState targetState = runs.playerState(target.getUniqueId()).orElseThrow();
        if (!"DOWNED".equals(targetState.lifeState)) {
            return;
        }
        if (!withinReviveRange(reviver, target)) {
            return;
        }
        event.setCancelled(true);
        double multiplier = growth.reviveSpeedMultiplier(reviver);
        long duration = Math.max(1000L, Math.round(plugin.getConfig().getInt("prototype.revive-channel-seconds", 3) * 1000L / multiplier));
        reviveChannels.put(target.getUniqueId(), new ReviveChannel(reviver.getUniqueId(), target.getUniqueId(), Instant.now().toEpochMilli() + duration));
        reviver.sendMessage(ChatColor.YELLOW + target.getName() + " 구조 시작 — " + (duration / 1000.0) + "초");
        ActionBarService.critical(reviver, Component.text(target.getName() + " 구조 시작", NamedTextColor.YELLOW), 30);
        ActionBarService.critical(target, Component.text(reviver.getName() + "이(가) 구조 중", NamedTextColor.YELLOW), 30);
    }

    private boolean routeLeft(Player player) {
        if (!inCombatStance(player) || !requireActiveAction(player)) return false;
        long tick = Bukkit.getCurrentTick();
        if (lastLeftInputTick.getOrDefault(player.getUniqueId(), Long.MIN_VALUE) == tick) {
            return lastLeftHit.getOrDefault(player.getUniqueId(), false);
        }
        lastLeftInputTick.put(player.getUniqueId(), tick);
        boolean result;
        if (player.isSneaking()) {
            result = executeWeaponActive(player, 2);
        } else {
            if ("PICKAXE".equals(equipment.resolveWeaponId(player)) && player.getTargetBlockExact(4) != null
                    && nearestTarget(player, content.weapon("PICKAXE").range()).isEmpty()) {
                lastLeftHit.put(player.getUniqueId(), false);
                return false;
            }
            result = executeBasicAttack(player);
        }
        lastLeftHit.put(player.getUniqueId(), result);
        returnToCombatStance(player);
        return result;
    }

    private boolean executeBasicAttack(Player player) {
        String weaponId = equipment.resolveWeaponId(player);
        PrototypeContent.WeaponDefinition weapon = contentWeapon(player, weaponId);
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        if ("TRIDENT".equals(weaponId) && !"HELD".equals(state.tridentState)) {
            ActionBarService.notice(player, Component.text("삼지창을 먼저 회수하세요 (Shift+R)", NamedTextColor.RED), 40);
            return false;
        }
        long now = System.nanoTime();
        if (attackReadyAtNanos.getOrDefault(player.getUniqueId(), 0L) > now) {
            return false;
        }
        double cooldownMultiplier = runs.isTestRun()
                ? clamp(state.testCooldownMultiplier, 0.05, 10.0) : 1.0;
        attackReadyAtNanos.put(player.getUniqueId(), now
                + CombatMath.cooldownTicks(weapon.intervalTicks(), cooldownMultiplier) * 50_000_000L);
        ComboState combo = combos.computeIfAbsent(player.getUniqueId(), ignored -> new ComboState());
        if (!weaponId.equals(combo.weaponId) || Instant.now().toEpochMilli() - combo.lastAttackAtEpochMs > 1250L) {
            combo.weaponId = weaponId;
            combo.stage = 0;
        }
        int stage = combo.stage % weapon.attackCoefficients().size();
        combo.stage = (stage + 1) % weapon.attackCoefficients().size();
        combo.lastAttackAtEpochMs = Instant.now().toEpochMilli();
        String executionId = UUID.randomUUID().toString();
        if ("BOW".equals(weaponId)) {
            if (!takeOneMaterial(player, Material.ARROW)) {
                attackReadyAtNanos.remove(player.getUniqueId());
                ActionBarService.notice(player, Component.text("화살이 필요합니다", NamedTextColor.RED), 30);
                return false;
            }
            Arrow arrow = player.getWorld().spawnArrow(player.getEyeLocation(), player.getEyeLocation().getDirection(), 2.8f, 0.0f);
            arrow.setShooter(player);
            arrow.setDamage(0.0);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.getPersistentDataContainer().set(projectileOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            arrow.getPersistentDataContainer().set(projectileWeaponKey, PersistentDataType.STRING, weaponId);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.1f);
            return true;
        }
        List<LivingEntity> targets = coneTargets(player, weapon.range(), weapon.arcDegrees(), "UNARMED".equals(weaponId) ? 2 : 3);
        for (LivingEntity target : targets) {
            double raw = 100.0 * weapon.attackCoefficients().get(stage);
            double breakDamage = weapon.breakDamage().get(Math.min(stage, weapon.breakDamage().size() - 1));
            damageCombatEntity(player, target, raw, breakDamage, executionId + ":" + target.getUniqueId());
            applyWeaponStatus(player, target, weapon, stage, executionId);
        }
        if (!targets.isEmpty()) player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6f, 1.15f);
        return !targets.isEmpty();
    }

    private boolean executeWeaponActive(Player player, int slot) {
        if (!requireActiveAction(player)) return false;
        String weaponId = equipment.resolveWeaponId(player);
        PrototypeContent.SkillDefinition skill = skills.resolve(player, slot);
        if (skill == null) {
            ActionBarService.notice(player, Component.text("W" + slot + " 스킬이 비어 있습니다. Shift+F → 스킬에서 장착하세요.", NamedTextColor.RED), 50);
            return false;
        }
        if ("TRIDENT_THROW".equals(skill.effect())) {
            boolean success = throwTrident(player, skill.apCost());
            if (success) showSkillEffect(player, skill, List.of());
            return success;
        }
        if ("TRIDENT_RECALL".equals(skill.effect())) {
            boolean success = recallTrident(player, skill.apCost());
            if (success) showSkillEffect(player, skill, List.of());
            return success;
        }
        if ("BOW".equals(weaponId) && !hasMaterial(player, Material.ARROW)) {
            ActionBarService.notice(player, Component.text("화살이 필요합니다", NamedTextColor.RED), 30);
            return false;
        }
        if (!runs.consumeAp(player, skill.apCost())) {
            apFailure(player, skill.apCost());
            return false;
        }
        if ("BOW".equals(weaponId)) takeOneMaterial(player, Material.ARROW);
        List<LivingEntity> targets = coneTargets(player, skill.range(), skill.arcDegrees(), skill.maxTargets());
        String executionId = "skill:" + skill.id() + ":" + UUID.randomUUID();
        for (LivingEntity target : targets) {
            damageCombatEntity(player, target, 100.0 * skill.damageCoefficient(), skill.breakDamage(),
                    executionId + ":" + target.getUniqueId());
            applySkillEffect(target, skill);
        }
        showSkillEffect(player, skill, targets);
        runs.mutate(run -> run.players.get(player.getUniqueId().toString()).tutorialSignals.add("USED_SKILL"));
        ActionBarService.notice(player, Component.text("W" + slot + " " + skill.name() + " / AP -" + Math.round(skill.apCost()), NamedTextColor.AQUA), 30);
        return true;
    }

    private void executeCommonActive(Player player, int slot) {
        if (!requireActiveAction(player)) return;
        switch (slot) {
            case 1 -> executeDodge(player);
            case 2 -> {
                if (!runs.consumeAp(player, 24.0)) {
                    apFailure(player, 24.0);
                    return;
                }
                for (Player member : runs.onlineMembers()) {
                    if (member.getLocation().distanceSquared(player.getLocation()) <= 64.0) {
                        runs.mutate(run -> {
                            RunSnapshot.PlayerState state = run.players.get(member.getUniqueId().toString());
                            state.ap = Math.min(state.maxAp, state.ap + 10.0);
                        });
                    }
                }
                runs.broadcast(ChatColor.AQUA + player.getName() + "의 집결 신호: 근처 파티 AP +10");
            }
            case 3 -> nearestTarget(player, 16.0).ifPresent(target -> {
                applyMark(target, 100L);
                ActionBarService.notice(player, Component.text("C3 전술 표식", NamedTextColor.YELLOW), 30);
            });
            case 4 -> {
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 1.3f);
                for (Player member : runs.onlineMembers()) {
                    member.sendMessage(ChatColor.YELLOW + "[C4 위치 신호] " + player.getName() + " @ "
                            + player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ());
                }
            }
            default -> { }
        }
    }

    private void executeQuickItem(Player player, int slot) {
        if (!requireActiveAction(player)) return;
        String bound = equipment.quickBinding(player, slot);
        if (bound == null || !equipment.consumeQuickItem(player, bound)) {
            ActionBarService.notice(player, Component.text("Q" + slot + " 소모품 없음", NamedTextColor.RED), 30);
            return;
        }
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0
                : player.getAttribute(Attribute.MAX_HEALTH).getValue();
        String result;
        switch (bound) {
            case "RATION" -> {
                player.setHealth(Math.min(maxHealth, player.getHealth() + 8.0));
                runs.mutateTransient(run -> {
                    RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                    state.ap = Math.min(state.maxAp, state.ap + 20.0);
                });
                result = "체력 +8 / AP +20";
            }
            case "BANDAGE" -> {
                player.setHealth(Math.min(maxHealth, player.getHealth() + 12.0));
                result = "체력 +12";
            }
            case "ANTIDOTE" -> {
                player.removePotionEffect(PotionEffectType.POISON);
                player.removePotionEffect(PotionEffectType.WITHER);
                player.removePotionEffect(PotionEffectType.WEAKNESS);
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                result = "독·위더·약화·둔화 제거";
            }
            default -> { ActionBarService.notice(player, Component.text("지원하지 않는 Q 아이템 " + bound, NamedTextColor.RED), 40); return; }
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.0f);
        ActionBarService.notice(player, Component.text("Q" + slot + " " + content.item(bound).name() + ": " + result, NamedTextColor.GREEN), 40);
    }

    private void executeDodge(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        double cost = PlayerStatPolicy.dodgeCost(state.investedStats);
        if (!runs.consumeAp(player, cost)) {
            apFailure(player, cost);
            return;
        }
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        if (player.isSneaking()) {
            direction.multiply(-1.0);
        }
        player.setVelocity(direction.multiply(0.9 * PlayerStatPolicy.dodgeDistanceMultiplier(state.investedStats)).setY(0.12));
        invulnerableUntilEpochMs.put(player.getUniqueId(), Instant.now().plusMillis(450).toEpochMilli());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.35f, 1.6f);
        ActionBarService.notice(player, Component.text("◇ 회피 / AP -" + Math.round(cost), NamedTextColor.AQUA), 24);
    }

    private boolean throwTrident(Player player, double cost) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        if (!"HELD".equals(state.tridentState)) {
            ActionBarService.notice(player, Component.text("이미 투척 상태입니다", NamedTextColor.RED), 30);
            return false;
        }
        if (!runs.consumeAp(player, cost)) {
            apFailure(player, cost);
            return false;
        }
        Trident trident = player.getWorld().spawn(player.getEyeLocation(), Trident.class, entity -> {
            entity.setShooter(player);
            entity.setDamage(0.0);
            entity.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            entity.setVelocity(player.getEyeLocation().getDirection().multiply(2.2));
            entity.getPersistentDataContainer().set(projectileOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            entity.getPersistentDataContainer().set(projectileWeaponKey, PersistentDataType.STRING, "TRIDENT");
        });
        runs.mutate(run -> {
            RunSnapshot.PlayerState value = run.players.get(player.getUniqueId().toString());
            value.tridentState = "THROWN";
            value.tridentEntityUuid = trident.getUniqueId().toString();
            value.tridentThrownAtEpochMs = Instant.now().toEpochMilli();
        });
        runs.mutate(run -> run.players.get(player.getUniqueId().toString()).tutorialSignals.add("USED_SKILL"));
        ActionBarService.notice(player, Component.text("공명 투창 / AP -" + Math.round(cost), NamedTextColor.AQUA), 30);
        return true;
    }

    private boolean recallTrident(Player player, double cost) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        if ("HELD".equals(state.tridentState)) {
            ActionBarService.notice(player, Component.text("삼지창이 손에 있습니다", NamedTextColor.GRAY), 24);
            return false;
        }
        if (!"RETURNING".equals(state.tridentState) && !runs.consumeAp(player, cost)) {
            apFailure(player, cost);
            return false;
        }
        runs.mutate(run -> run.players.get(player.getUniqueId().toString()).tridentState = "RETURNING");
        runs.mutate(run -> run.players.get(player.getUniqueId().toString()).tutorialSignals.add("USED_SKILL"));
        ActionBarService.notice(player, Component.text("공명 회수 / AP -" + Math.round(cost), NamedTextColor.AQUA), 30);
        return true;
    }

    private void processTridents(long now) {
        for (Player player : runs.onlineMembers()) {
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
            if ("HELD".equals(state.tridentState)) {
                continue;
            }
            Entity entity = findEntity(state.tridentEntityUuid);
            if (entity == null) {
                if ("RETURNING".equals(state.tridentState)) {
                    recoverTrident(player);
                } else if (!"RECOVERABLE".equals(state.tridentState)) {
                    runs.mutate(run -> run.players.get(player.getUniqueId().toString()).tridentState = "RECOVERABLE");
                }
                continue;
            }
            storeThrownTrident(player, entity.getLocation(), entity.getUniqueId());
            if (now - state.tridentThrownAtEpochMs >= 45_000L && !"RETURNING".equals(state.tridentState)) {
                runs.mutate(run -> run.players.get(player.getUniqueId().toString()).tridentState = "RETURNING");
            }
            if (!"RETURNING".equals(state.tridentState)) {
                continue;
            }
            Vector delta = player.getEyeLocation().toVector().subtract(entity.getLocation().toVector());
            if (delta.lengthSquared() <= 2.25) {
                entity.remove();
                recoverTrident(player);
                continue;
            }
            entity.setGravity(false);
            entity.setVelocity(delta.normalize().multiply(1.2));
            for (Entity nearby : entity.getNearbyEntities(1.2, 1.2, 1.2)) {
                if (nearby instanceof LivingEntity target && isCombatEntity(target)
                        && tridentHitCooldown.getOrDefault(target.getUniqueId(), 0L) < now) {
                    tridentHitCooldown.put(target.getUniqueId(), now + 3000L);
                    damageCombatEntity(player, target, 75.0, 60.0,
                            "trident-return:" + state.tridentEntityUuid + ":" + target.getUniqueId());
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, true));
                }
            }
        }
    }

    private void recoverTrident(Player player) {
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            state.tridentState = "HELD";
            state.tridentEntityUuid = null;
            state.tridentWorld = null;
        });
        equipment.syncAuthoritativeEquipment(player);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.8f, 1.0f);
    }

    private void storeThrownTrident(Player player, Location location, UUID entityId) {
        runs.mutateTransient(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            if (!"RETURNING".equals(state.tridentState)) {
                state.tridentState = "THROWN";
            }
            state.tridentEntityUuid = entityId.toString();
            state.tridentWorld = location.getWorld().getName();
            state.tridentX = location.getX();
            state.tridentY = location.getY();
            state.tridentZ = location.getZ();
        });
    }

    private void enterDowned(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        if (!"ACTIVE".equals(state.lifeState)) {
            return;
        }
        runs.mutate(run -> {
            RunSnapshot.PlayerState value = run.players.get(player.getUniqueId().toString());
            value.lifeState = "DOWNED";
            value.downedAtEpochMs = Instant.now().toEpochMilli();
            value.ap = 0.0;
        });
        player.setHealth(1.0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 4, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
        runs.broadcast(ChatColor.RED + "[빈사] " + player.getName() + " — 우클릭 유지로 구조하세요.");
        for (Player member : runs.onlineMembers()) {
            ActionBarService.critical(member, Component.text("[빈사] " + player.getName() + " — 웅크리고 우클릭하여 구조", NamedTextColor.RED), 80);
        }
        telemetry.event(runs.current().orElseThrow().runId, "PLAYER_STATE_CHANGED", "{\"state\":\"DOWNED\"}");
    }

    private void processRevives(long now) {
        List<UUID> complete = new ArrayList<>();
        for (ReviveChannel channel : reviveChannels.values()) {
            Player reviver = Bukkit.getPlayer(channel.reviver);
            Player target = Bukkit.getPlayer(channel.target);
            if (reviver == null || target == null || !withinReviveRange(reviver, target)
                    || !reviver.isSneaking()) {
                if (reviver != null) ActionBarService.critical(reviver, Component.text("구조 취소", NamedTextColor.RED), 30);
                complete.add(channel.target);
                continue;
            }
            long remaining = Math.max(0L, channel.completeAtEpochMs - now);
            String progress = "구조 " + String.format(java.util.Locale.ROOT, "%.1f", remaining / 1000.0) + "초";
            ActionBarService.show(reviver, Component.text(progress, NamedTextColor.AQUA), 3, 100);
            ActionBarService.show(target, Component.text(reviver.getName() + " 구조 중 · " + progress, NamedTextColor.AQUA), 3, 100);
            if (now >= channel.completeAtEpochMs) {
                revive(target, reviver);
                complete.add(channel.target);
            }
        }
        complete.forEach(reviveChannels::remove);
    }

    private void revive(Player target, Player reviver) {
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(target.getUniqueId().toString());
            state.lifeState = "ACTIVE";
            state.downedAtEpochMs = 0L;
            state.ap = state.maxAp * 0.20;
        });
        target.removePotionEffect(PotionEffectType.SLOWNESS);
        target.removePotionEffect(PotionEffectType.GLOWING);
        target.setHealth(Math.max(1.0, target.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.25));
        runs.broadcast(ChatColor.GREEN + reviver.getName() + "이(가) " + target.getName() + "을 구조했습니다.");
        ActionBarService.critical(reviver, Component.text(target.getName() + " 구조 완료", NamedTextColor.GREEN), 60);
        ActionBarService.critical(target, Component.text("구조 완료 · 전투 복귀", NamedTextColor.GREEN), 60);
        telemetry.event(runs.current().orElseThrow().runId, "PLAYER_STATE_CHANGED", "{\"state\":\"ACTIVE\",\"reason\":\"REVIVED\"}");
    }

    private boolean withinReviveRange(Player reviver, Player target) {
        return reviver.getWorld().equals(target.getWorld())
                && reviver.getLocation().distanceSquared(target.getLocation()) <= REVIVE_RANGE_SQUARED;
    }

    private void processDownedTimeouts(long now) {
        long timeout = plugin.getConfig().getInt("prototype.downed-timeout-seconds", 30) * 1000L;
        for (Player player : runs.onlineMembers()) {
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
            if ("DOWNED".equals(state.lifeState) && now - state.downedAtEpochMs >= timeout) {
                runs.mutate(run -> run.players.get(player.getUniqueId().toString()).lifeState = "DEAD");
                player.setGameMode(GameMode.SPECTATOR);
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                player.removePotionEffect(PotionEffectType.GLOWING);
                runs.broadcast(ChatColor.DARK_RED + "[완전 사망] " + player.getName());
                for (Player member : runs.onlineMembers()) {
                    ActionBarService.critical(member, Component.text("[완전 사망] " + player.getName(), NamedTextColor.DARK_RED), 80);
                }
                telemetry.event(runs.current().orElseThrow().runId, "PLAYER_STATE_CHANGED", "{\"state\":\"DEAD\"}");
            }
        }
        if (runs.current().map(run -> "RUNNING".equals(run.state)).orElse(false) && runs.survivableCount() == 0) {
            try {
                runs.stop("PARTY_WIPED", "system");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private void defeatEnemy(Player attacker, LivingEntity target, String enemyId) {
        UUID entityId = target.getUniqueId();
        target.remove();
        activeCombatEntities.remove(entityId);
        BossBar bar = breakBars.remove(entityId);
        if (bar != null) {
            bar.removeAll();
        }
        PrototypeContent.EnemyDefinition definition = contentEnemy(enemyId);
        boolean rewarded = runs.commitOnce("enemy-defeat:" + entityId, "ENEMY_DEFEATED",
                "{\"enemyId\":\"" + enemyId + "\"}", run -> { });
        if (rewarded) for (var drop : definition.drops().entrySet()) {
            int amount = Math.max(1, (int) Math.floor(drop.getValue() * growth.resourceMultiplier(attacker)));
            itemRewardHandler.reward(attacker, drop.getKey(), amount);
        }
        for (Player member : runs.onlineMembers()) {
            growth.awardExp(member, definition.activityExp(), "enemy-exp:" + entityId + ":" + member.getUniqueId());
        }
    }

    private void applyWeaponStatus(Player attacker, LivingEntity target, PrototypeContent.WeaponDefinition weapon, int stage,
                                   String executionId) {
        double roll = Math.floorMod((executionId + ":" + target.getUniqueId()).hashCode(), 10_000) / 10_000.0;
        RunSnapshot.PlayerState state = runs.playerState(attacker.getUniqueId()).orElse(null);
        double chance = weapon.statusChance() + (state == null ? 0.0 : PlayerStatPolicy.statusChanceBonus(state.investedStats));
        if (stage != weapon.attackCoefficients().size() - 1 || roll > chance) {
            return;
        }
        long expiry = Instant.now().plusSeconds(4).toEpochMilli();
        NamespacedKey key = new NamespacedKey(plugin, "status_" + weapon.statusId().toLowerCase(java.util.Locale.ROOT));
        target.getPersistentDataContainer().set(key, PersistentDataType.LONG, expiry);
        switch (weapon.statusId()) {
            case "MARK" -> applyMark(target, 80L);
            case "SLOW" -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, true));
            case "ARMOR_SHRED" -> {
                double defence = target.getPersistentDataContainer().getOrDefault(defenceKey, PersistentDataType.DOUBLE, 0.0);
                target.getPersistentDataContainer().set(defenceKey, PersistentDataType.DOUBLE, Math.max(0.0, defence - 10.0));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (target.isValid()) {
                        double current = target.getPersistentDataContainer().getOrDefault(defenceKey, PersistentDataType.DOUBLE, 0.0);
                        target.getPersistentDataContainer().set(defenceKey, PersistentDataType.DOUBLE, current + 10.0);
                    }
                }, 80L);
            }
            default -> { }
        }
        telemetry.event(runs.current().orElseThrow().runId, "STATUS_APPLIED",
                "{\"statusId\":\"" + weapon.statusId() + "\",\"source\":\"" + attacker.getUniqueId() + "\"}");
    }

    private List<LivingEntity> coneTargets(Player player, double range, double arcDegrees, int maximum) {
        Location origin = player.getEyeLocation();
        Vector facing = origin.getDirection().normalize();
        double minimumDot = Math.cos(Math.toRadians(arcDegrees / 2.0));
        Predicate<Entity> eligible = entity -> entity instanceof LivingEntity living && living != player && isCombatEntity(living);
        return player.getWorld().getNearbyEntities(origin, range, range, range, eligible).stream()
                .map(entity -> (LivingEntity) entity)
                .filter(entity -> {
                    Vector direction = entity.getEyeLocation().toVector().subtract(origin.toVector());
                    return direction.lengthSquared() <= range * range && facing.dot(direction.normalize()) >= minimumDot
                            && player.hasLineOfSight(entity);
                })
                .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
                .limit(maximum)
                .toList();
    }

    private java.util.Optional<LivingEntity> nearestTarget(Player player, double range) {
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(),
                range, 0.8, entity -> entity instanceof LivingEntity living && isCombatEntity(living));
        return result != null && result.getHitEntity() instanceof LivingEntity living
                ? java.util.Optional.of(living) : java.util.Optional.empty();
    }

    private void updateHud() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || !"RUNNING".equals(snapshot.state)) {
            return;
        }
        for (Player player : runs.onlineMembers()) {
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
            NamedTextColor color = state.ap <= 20.0 ? NamedTextColor.RED : NamedTextColor.AQUA;
            ActionBarService.renderHud(player, Component.text("Day " + snapshot.day + " | AP " + Math.round(state.ap) + "/" + state.maxAp
                    + " | Lv." + state.level + " | " + equipment.resolveWeaponId(player), color));
        }
    }

    private void updateBreakBar(LivingEntity target, double current, double maximum) {
        BossBar bar = breakBars.computeIfAbsent(target.getUniqueId(), ignored -> {
            BossBar created = Bukkit.createBossBar("BREAK — " + (target.getCustomName() == null ? enemyId(target) : target.getCustomName()),
                    BarColor.WHITE, BarStyle.SEGMENTED_10);
            runs.onlineMembers().forEach(created::addPlayer);
            return created;
        });
        bar.setProgress(Math.max(0.0, Math.min(1.0, current / maximum)));
        bar.setVisible(true);
    }

    private void refreshBreakBars() {
        breakBars.entrySet().removeIf(entry -> {
            Entity entity = findEntity(entry.getKey().toString());
            if (!(entity instanceof LivingEntity living) || !living.isValid()) {
                entry.getValue().removeAll();
                return true;
            }
            PersistentDataContainer pdc = living.getPersistentDataContainer();
            double current = pdc.getOrDefault(breakKey, PersistentDataType.DOUBLE, 0.0);
            double max = pdc.getOrDefault(breakMaxKey, PersistentDataType.DOUBLE, 0.0);
            entry.getValue().setProgress(max <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, current / max)));
            return false;
        });
    }

    private boolean isCombatEntity(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String runId = pdc.get(enemyRunIdKey, PersistentDataType.STRING);
        return pdc.has(enemyIdKey, PersistentDataType.STRING)
                && runs.current().map(run -> run.runId.equals(runId)).orElse(false);
    }

    private String enemyId(Entity entity) {
        return entity.getPersistentDataContainer().getOrDefault(enemyIdKey, PersistentDataType.STRING, "UNKNOWN");
    }

    private double enemyAttackDamage(LivingEntity attacker) {
        Double override = attacker.getPersistentDataContainer().get(attackDamageOverrideKey, PersistentDataType.DOUBLE);
        if (override != null) {
            return override;
        }
        String id = enemyId(attacker);
        if (id.startsWith("BOSS-")) {
            return 110.0;
        }
        return contentEnemy(id).attackDamage();
    }

    private LivingEntity combatAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity living && isCombatEntity(living)) {
            return living;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof LivingEntity living && isCombatEntity(living)) {
            return living;
        }
        return null;
    }

    private PrototypeContent.EnemyDefinition contentEnemy(String id) {
        return content.enemy(id);
    }

    private PrototypeContent.WeaponDefinition contentWeapon(Player ignored, String id) {
        return content.weapon(id);
    }

    private Entity findEntity(String uuid) {
        if (uuid == null) {
            return null;
        }
        try {
            UUID id = UUID.fromString(uuid);
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                Entity entity = world.getEntity(id);
                if (entity != null) {
                    return entity;
                }
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private void apFailure(Player player, double required) {
        double current = runs.playerState(player.getUniqueId()).map(state -> state.ap).orElse(0.0);
        ActionBarService.notice(player, Component.text("AP 부족: 필요 " + Math.round(required) + " / 현재 " + Math.round(current), NamedTextColor.RED), 35);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.7f);
    }

    private boolean requireActiveAction(Player player) {
        if (!isActionRestricted(player)) return true;
        showRestrictedAction(player);
        return false;
    }

    private boolean isActionRestricted(Player player) {
        return runs.playerState(player.getUniqueId())
                .map(state -> !"ACTIVE".equals(state.lifeState)).orElse(true);
    }

    private void showRestrictedAction(Player player) {
        ActionBarService.notice(player,
                Component.text("빈사·사망 상태에서는 이동과 도움 요청 외 행동을 할 수 없습니다.", NamedTextColor.RED), 30);
    }

    private void applySkillEffect(LivingEntity target, PrototypeContent.SkillDefinition skill) {
        switch (skill.effect()) {
            case "SLOW" -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, true, true));
            case "MARK" -> {
                applyMark(target, 120L);
            }
            case "ARMOR_SHRED" -> {
                double before = target.getPersistentDataContainer().getOrDefault(defenceKey, PersistentDataType.DOUBLE, 0.0);
                double reduction = Math.min(20.0, before);
                target.getPersistentDataContainer().set(defenceKey, PersistentDataType.DOUBLE, before - reduction);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (target.isValid()) {
                        double current = target.getPersistentDataContainer().getOrDefault(defenceKey, PersistentDataType.DOUBLE, 0.0);
                        target.getPersistentDataContainer().set(defenceKey, PersistentDataType.DOUBLE, current + reduction);
                    }
                }, 100L);
            }
            default -> { }
        }
    }

    private void showSkillEffect(Player player, PrototypeContent.SkillDefinition skill, List<LivingEntity> targets) {
        Particle particle;
        Sound sound;
        try { particle = Particle.valueOf(skill.particle()); }
        catch (IllegalArgumentException exception) { particle = Particle.CRIT; }
        sound = Registry.SOUND_EVENT.get(NamespacedKey.minecraft(
                skill.sound().toLowerCase(java.util.Locale.ROOT).replace('_', '.')));
        if (sound == null) sound = Sound.ENTITY_PLAYER_ATTACK_STRONG;
        Vector direction = player.getEyeLocation().getDirection().normalize();
        for (int step = 1; step <= 6; step++) {
            Location point = player.getEyeLocation().clone().add(direction.clone().multiply(skill.range() * step / 6.0));
            player.getWorld().spawnParticle(particle, point, 4, 0.18, 0.18, 0.18, 0.01);
        }
        for (LivingEntity target : targets) {
            target.getWorld().spawnParticle(particle, target.getLocation().add(0, target.getHeight() * 0.6, 0),
                    18, 0.45, 0.5, 0.45, 0.03);
        }
        player.getWorld().playSound(player.getLocation(), sound, 0.85f, 1.1f);
    }

    private void applyMark(LivingEntity target, long durationTicks) {
        NamespacedKey key = new NamespacedKey(plugin, "status_mark");
        long expiry = Instant.now().plusMillis(durationTicks * 50L).toEpochMilli();
        target.setGlowing(true);
        target.getPersistentDataContainer().set(key, PersistentDataType.LONG, expiry);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!target.isValid()) return;
            long currentExpiry = target.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L);
            if (currentExpiry <= Instant.now().toEpochMilli()) {
                target.setGlowing(false);
                target.getPersistentDataContainer().remove(key);
            }
        }, durationTicks);
    }

    private boolean hasMaterial(Player player, Material material) {
        for (int slot = 1; slot <= 35; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && item.getType() == material && item.getAmount() > 0) return true;
        }
        return false;
    }

    private boolean takeOneMaterial(Player player, Material material) {
        for (int slot = 1; slot <= 35; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType() != material || item.getAmount() <= 0) continue;
            item.setAmount(item.getAmount() - 1);
            if (item.getAmount() <= 0) player.getInventory().setItem(slot, null);
            return true;
        }
        return false;
    }

    private boolean inCombatStance(Player player) {
        return player.getInventory().getHeldItemSlot() == 0;
    }

    private void returnToCombatStance(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (runs.isRunningMember(player)) player.getInventory().setHeldItemSlot(0);
        });
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record CombatEntityView(String uuid, String enemyId, String entityType, double health,
                                   double maxHealth, double defence, double currentBreak, double maxBreak,
                                   double attackDamage, boolean ai, boolean invulnerable, List<String> statuses) {
    }

    public record DamagePreview(double rawDamage, double defenceFactor, double augmentDamageMultiplier,
                                double testDamageMultiplier, double finalDamage, double rawBreak,
                                double augmentBreakMultiplier, double testBreakMultiplier, double finalBreak) {
    }

    private static final class ComboState {
        private String weaponId;
        private int stage;
        private long lastAttackAtEpochMs;
    }

    private record ReviveChannel(UUID reviver, UUID target, long completeAtEpochMs) {}

    public interface BossDamageHandler {
        void damage(Player attacker, LivingEntity boss, double damage, double breakDamage, String executionId);
    }

    @FunctionalInterface
    public interface ItemRewardHandler {
        void reward(Player player, String resourceId, int amount);
    }
}
