package com.lsc.corp.wsplugin.combat;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.facility.FacilityService;
import com.lsc.corp.wsplugin.facility.FacilityStateAccess;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.EquipmentService;
import com.lsc.corp.wsplugin.player.PlayerStatPolicy;
import com.lsc.corp.wsplugin.player.SkillLoadoutService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import com.lsc.corp.wsplugin.status.StatusService;
import com.lsc.corp.wsplugin.status.StatusRuntimePolicy;
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
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
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
    private final ProductionContentCatalog production;
    private final EquipmentService equipment;
    private final GrowthService growth;
    private final SkillLoadoutService skills;
    private final TelemetryService telemetry;
    private final StatusService statuses;
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
    private final Map<String, Long> skillReadyAtNanos = new HashMap<>();
    private final Map<UUID, Long> lastLeftInputTick = new HashMap<>();
    private final Map<UUID, CombatInputPolicy.LeftDisposition> lastLeftDisposition = new HashMap<>();
    private final Map<UUID, Long> invulnerableUntilEpochMs = new HashMap<>();
    private final Map<UUID, Long> lastSneakAtEpochMs = new HashMap<>();
    private final Set<UUID> lifeMovementProfiles = new HashSet<>();
    private final Set<UUID> permittedRestrictedTeleports = new HashSet<>();
    private final Map<UUID, ReviveSession> reviveSessions = new HashMap<>();
    private final Map<UUID, BossBar> breakBars = new HashMap<>();
    private final Set<UUID> activeCombatEntities = new HashSet<>();
    private final Map<UUID, Long> tridentHitCooldown = new HashMap<>();
    private final Map<UUID, CoverField> coverFields = new HashMap<>();
    private final Map<UUID, Map<UUID, Double>> enemyContributions = new HashMap<>();
    private final Map<UUID, EnemyActionState> productionActionStates = new HashMap<>();
    private final Map<EnemyHitPermit, Double> permittedEnemyHits = new HashMap<>();
    private final Map<UUID, QuickUseChannel> quickUseChannels = new HashMap<>();
    private BossDamageHandler bossDamageHandler;
    private ProductionLootHandler productionLootHandler = (transactionId, enemy, contributors) -> { };
    private Consumer<Player> menuOpener = player -> { };
    private ItemRewardHandler itemRewardHandler = (player, resourceId, amount) -> { };
    private DamageNumberService damageNumbers;
    private FacilityService facilityService;
    private AmmoService ammoService;
    private DeathHandler deathHandler = (player, reason) -> false;
    private boolean scannedPersistedEntities;
    private int hudTick;

    public CombatService(JavaPlugin plugin, RunService runs, PrototypeContent content,
                         ProductionContentCatalog production, EquipmentService equipment,
                         GrowthService growth, SkillLoadoutService skills, TelemetryService telemetry,
                         StatusService statuses) {
        this.plugin = plugin;
        this.runs = runs;
        this.content = content;
        this.production = production;
        this.equipment = equipment;
        this.growth = growth;
        this.skills = skills;
        this.telemetry = telemetry;
        this.statuses = statuses;
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

    public void setProductionLootHandler(ProductionLootHandler productionLootHandler) {
        this.productionLootHandler = java.util.Objects.requireNonNull(productionLootHandler);
    }

    public void setFacilityService(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    public void setAmmoService(AmmoService ammoService) {
        this.ammoService = java.util.Objects.requireNonNull(ammoService);
    }

    public void setDeathHandler(DeathHandler deathHandler) {
        this.deathHandler = java.util.Objects.requireNonNull(deathHandler);
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

    public LivingEntity spawnProductionEnemy(String rawId, Location location, double combatScale) {
        String id = rawId.toUpperCase(java.util.Locale.ROOT);
        ProductionContentCatalog.EnemyEntry definition = production.enemiesById().get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown production enemy " + rawId);
        EntityType type;
        try {
            type = EntityType.valueOf(definition.bukkitType());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid production living entity type " + definition.bukkitType(), exception);
        }
        if (!type.isAlive()) throw new IllegalArgumentException("Production entity type is not living " + type);
        double scale = clamp(combatScale, 0.1, 10.0);
        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, type);
        tagCombatEntity(entity, definition.id(), definition.baseHp() * scale,
                definition.defence(), definition.breakMax() * Math.max(0.5, scale));
        entity.setCustomName(ChatColor.RED + definition.name() + ChatColor.GRAY + " [" + definition.role() + "]");
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
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

    public void damagePlayerFromPattern(LivingEntity attacker, Player target, double rawDamage) {
        if (!isCombatEntity(attacker) || rawDamage <= 0.0 || !runs.isRunningMember(target)) return;
        EnemyHitPermit permit = new EnemyHitPermit(attacker.getUniqueId(), target.getUniqueId());
        permittedEnemyHits.put(permit, rawDamage);
        try {
            target.damage(Math.max(0.1, rawDamage / PLAYER_HP_SCALE), attacker);
        } finally {
            permittedEnemyHits.remove(permit);
        }
    }

    public void tick() {
        long now = Instant.now().toEpochMilli();
        processQuickUseChannels();
        processProductionEnemyActions();
        processRevives(now);
        processLifeStates(now);
        processTridents(now);
        hudTick++;
        if (hudTick % 5 == 0) synchronizeLifeMovement();
        if (hudTick % 10 == 0) enforceSpectatorBoundaries();
        if (hudTick % 20 == 0) trackLastSafeLocations(now);
        if (hudTick % 10 == 0) {
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
        enemyContributions.clear();
        productionActionStates.clear();
        permittedEnemyHits.clear();
        breakBars.values().forEach(BossBar::removeAll);
        breakBars.clear();
    }

    public double damageCombatEntity(Player attacker, LivingEntity target, double rawAttack, double breakDamage, String executionId) {
        if (!isCombatEntity(target)) {
            return 0.0;
        }
        UUID tauntTarget = statuses.tauntTarget(attacker).orElse(null);
        if (!StatusRuntimePolicy.tauntAllows(tauntTarget, target.getUniqueId())) {
            ActionBarService.notice(attacker, Component.text("도발 대상 외에는 공격할 수 없습니다.", NamedTextColor.RED), 24);
            return 0.0;
        }
        String id = enemyId(target);
        PersistentDataContainer pdc = target.getPersistentDataContainer();
        double defence = Math.max(0.0, pdc.getOrDefault(defenceKey, PersistentDataType.DOUBLE, 0.0)
                - statuses.strength(target, "ARMOR_BREAK"));
        long groggyUntil = pdc.getOrDefault(groggyUntilKey, PersistentDataType.LONG, 0L);
        double groggyMultiplier = groggyUntil > Instant.now().toEpochMilli() ? 1.15 : 1.0;
        RunSnapshot.PlayerState attackerState = runs.playerState(attacker.getUniqueId()).orElse(null);
        double testDamageMultiplier = runs.isTestRun() && attackerState != null
                ? clamp(attackerState.testDamageDealtMultiplier, 0.0, 100.0) : 1.0;
        double testBreakMultiplier = runs.isTestRun() && attackerState != null
                ? clamp(attackerState.testBreakMultiplier, 0.0, 100.0) : 1.0;
        EquipmentService.ActiveEquipmentStats equipmentStats = equipment.activeStats(attacker);
        double equipmentAttack = equipmentStats.value("ATK");
        GrowthService.ReactiveAttackModifier reactive = growth.reactiveAttackModifier(attacker, executionId);
        double effectiveDefence = Math.max(0.0,
                defence * (1.0 - reactive.defenceIgnoreFraction()) - equipmentStats.value("PEN"));
        double vulnerableMultiplier = 1.0 + Math.max(0.0, statuses.strength(target, "VULNERABLE"));
        double weaknessMultiplier = Math.max(0.10, 1.0 - Math.max(0.0, statuses.strength(attacker, "WEAKNESS")));
        boolean targetHasDot = hasDamageOverTime(target);
        double finalDamage = CombatMath.outgoingDamage(rawAttack + equipmentAttack, effectiveDefence,
                growth.attackMultiplier(attacker) * reactive.damageMultiplier() * weaknessMultiplier,
                groggyMultiplier * vulnerableMultiplier, testDamageMultiplier);
        double breakCallMultiplier = statusActive(target, "break_call") ? 1.06 : 1.0;
        double finalBreak = Math.max(0.0, breakDamage * growth.breakMultiplier(attacker) * reactive.breakMultiplier()
                * growth.targetBreakMultiplier(attacker, targetHasDot)
                * testBreakMultiplier * breakCallMultiplier * (1.0 + equipmentStats.value("BREAK_DAMAGE") / 100.0));
        if (pdc.getOrDefault(testInvulnerableKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1) {
            finalDamage = 0.0;
            finalBreak = 0.0;
        }
        if (id.startsWith("BOSS-") && bossDamageHandler != null) {
            bossDamageHandler.damage(attacker, target, finalDamage, finalBreak, executionId);
            if (damageNumbers != null) damageNumbers.show(attacker, target, finalDamage);
            return finalDamage;
        }
        if (finalDamage > 0.0 && production.enemiesById().containsKey(id)) {
            enemyContributions.computeIfAbsent(target.getUniqueId(), ignored -> new HashMap<>())
                    .merge(attacker.getUniqueId(), finalDamage, Double::sum);
        }
        if (finalDamage > 0.0) statuses.remove(target, "SLEEP", 64);
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
        StatusService.ApplyResult runtime = statuses.applyTestOverride(target, id, durationTicks / 20.0,
                Math.max(0.0, amplifier + 1.0), "test-status:" + UUID.randomUUID());
        if (runtime.applied() || runtime.convertedToBreak()) return;
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
        statuses.clearAll(target);
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
        EquipmentService.ActiveEquipmentStats equipmentStats = equipment.activeStats(attacker);
        double effectiveDefence = Math.max(0.0, view.defence() - equipmentStats.value("PEN"));
        double defenceFactor = 100.0 / (100.0 + effectiveDefence);
        double augmentDamage = growth.attackMultiplier(attacker);
        double augmentBreak = growth.breakMultiplier(attacker);
        double testDamage = runs.isTestRun() ? clamp(state.testDamageDealtMultiplier, 0.0, 100.0) : 1.0;
        double testBreak = runs.isTestRun() ? clamp(state.testBreakMultiplier, 0.0, 100.0) : 1.0;
        return new DamagePreview(rawDamage, defenceFactor, augmentDamage, testDamage,
                Math.max(0.0, (rawDamage + equipmentStats.value("ATK")) * defenceFactor
                        * augmentDamage * testDamage), rawBreak,
                augmentBreak, testBreak, Math.max(0.0, rawBreak * augmentBreak * testBreak
                        * (1.0 + equipmentStats.value("BREAK_DAMAGE") / 100.0)));
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
            if (byEntity.getDamager() instanceof Projectile && protectedByCover(player)) {
                event.setCancelled(true);
                player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation().add(0, 1, 0),
                        12, 0.6, 0.8, 0.6, Material.COBBLESTONE.createBlockData());
                return;
            }
            LivingEntity attacker = combatAttacker(byEntity);
            if (attacker != null) {
                if (isCombatEntity(attacker)) markCombatAction(player);
                EnemyHitPermit permit = new EnemyHitPermit(attacker.getUniqueId(), player.getUniqueId());
                Double permittedDamage = permittedEnemyHits.remove(permit);
                ProductionContentCatalog.EnemyEntry productionEnemy = production.enemiesById().get(enemyId(attacker));
                if (permittedDamage != null) {
                    event.setDamage(permittedDamage / PLAYER_HP_SCALE);
                } else if (productionEnemy != null) {
                    event.setCancelled(true);
                    return;
                } else {
                    event.setDamage(enemyAttackDamage(attacker) / PLAYER_HP_SCALE);
                }
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
        double equipmentDefence = equipment.activeStat(player, "DEF") + growth.bonusDefence(player);
        event.setDamage(event.getDamage() * PlayerStatPolicy.incomingDamageMultiplier(playerState.investedStats)
                * (100.0 / (100.0 + Math.max(0.0, equipmentDefence)))
                * (1.0 + Math.max(0.0, statuses.strength(player, "VULNERABLE"))));
        long now = Instant.now().toEpochMilli();
        if ("DOWNED_GRACE".equals(playerState.lifeState)) {
            event.setCancelled(true);
            return;
        }
        if ("DOWNED".equals(playerState.lifeState) || "BEING_REVIVED".equals(playerState.lifeState)) {
            event.setCancelled(true);
            applyDownedDamage(player, event.getFinalDamage(), downedDamageMultiplier(event));
            return;
        }
        if (now < playerState.reviveProtectionUntilEpochMs && isDamageOverTime(event.getCause())) {
            event.setCancelled(true);
            return;
        }
        if (invulnerableUntilEpochMs.getOrDefault(player.getUniqueId(), 0L) >= now) {
            event.setCancelled(true);
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 8, 0.4, 0.2, 0.4, 0.02);
            double augmentRefund = growth.onPreciseDodge(player);
            runs.mutate(run -> {
                RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                state.ap = Math.min(state.maxAp, state.ap + 10.0 + augmentRefund);
            });
            telemetry.event(runs.current().orElseThrow().runId, "DODGE_SUCCESS",
                    "{\"refund\":" + round(10.0 + augmentRefund) + "}");
            return;
        }
        event.setDamage(event.getDamage() * growth.reactiveIncomingDamageMultiplier(player));
        event.setDamage(event.getDamage() * DeathRuntimePolicy.recoveryDamageMultiplier(now,
                playerState.reviveProtectionUntilEpochMs, playerState.reviveTailProtectionUntilEpochMs));
        if (reviveProtected(player)) event.setDamage(event.getDamage() * 0.50);
        interruptReviveContributionOnDamage(player, event.getFinalDamage());
        growth.onIncomingDamage(player, event.getFinalDamage());
        switch (PlayerLifePolicy.evaluate(playerState.lifeState, player.getHealth(), event.getFinalDamage())) {
            case BLOCK -> event.setCancelled(true);
            case ENTER_DOWNED -> {
                event.setCancelled(true);
                if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                    if (playerState.voidRescueUsed) {
                        completeDeath(player, "VOID_RESCUE_EXHAUSTED");
                        break;
                    }
                    rescueFromVoid(player, now);
                }
                enterDowned(player);
            }
            case ALLOW -> {
                if (event.getFinalDamage() > 0.0) {
                    statuses.remove(player, "SLEEP", 64);
                    runs.mutate(run -> {
                        RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                        state.lastDamageAtEpochMs = now;
                        state.apRegenBlockedUntilEpochMs = now + 1500L;
                    });
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
            CombatInputPolicy.LeftDisposition disposition = routeLeft(player);
            if (action == Action.LEFT_CLICK_BLOCK && disposition.cancelsBlockDamage()) event.setCancelled(true);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLifeStateMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!runs.isRunningMember(player) || !event.hasChangedPosition() || event.getTo() == null) return;
        if (event instanceof PlayerTeleportEvent && permittedRestrictedTeleports.contains(player.getUniqueId())) return;
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if ("DOWNED_GRACE".equals(state.lifeState)) {
            Location locked = from.clone();
            locked.setYaw(to.getYaw());
            locked.setPitch(to.getPitch());
            event.setTo(locked);
            return;
        }
        if (!("DOWNED".equals(state.lifeState) || "BEING_REVIVED".equals(state.lifeState))) return;
        if ((player.isClimbing() && to.getY() > from.getY())
                || (player.isSwimming() && to.getY() < from.getY())) {
            Location limited = to.clone();
            limited.setY(from.getY());
            event.setTo(limited);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRestrictedTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!runs.isRunningMember(player) || permittedRestrictedTeleports.contains(player.getUniqueId())) return;
        if (runs.playerState(player.getUniqueId()).map(state -> isDowned(state.lifeState)).orElse(false)) {
            event.setCancelled(true);
            showRestrictedAction(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRestrictedMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player && runs.isRunningMember(player) && isActionRestricted(player)) {
            event.setCancelled(true);
            showRestrictedAction(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDownedKnockback(EntityKnockbackEvent event) {
        if (!(event.getEntity() instanceof Player player) || !runs.isRunningMember(player)) return;
        String lifeState = runs.playerState(player.getUniqueId()).map(state -> state.lifeState).orElse("");
        double fraction = DeathRuntimePolicy.knockbackFraction(lifeState);
        if (fraction < 1.0) event.setKnockback(event.getKnockback().clone().multiply(fraction));
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
        CombatInputPolicy.SlotChangeDisposition disposition = CombatInputPolicy.slotChange(
                event.getPreviousSlot(), slot, player.isSneaking());
        switch (disposition) {
            case COMMON_ACTIVE -> {
                event.setCancelled(true);
                executeCommonActive(player, slot);
            }
            case QUICK_ITEM -> {
                event.setCancelled(true);
                executeQuickItem(player, slot - 4);
            }
            case VANILLA -> {
                return;
            }
        }
        if (disposition.returnsToCombatStance()) returnToCombatStance(player);
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking() || !runs.isRunningMember(player)) {
            return;
        }
        RunSnapshot.PlayerState life = runs.playerState(player.getUniqueId()).orElse(null);
        if (life != null && ("DOWNED".equals(life.lifeState) || "BEING_REVIVED".equals(life.lifeState))) {
            requestHelp(player, life);
            return;
        }
        if (!inCombatStance(player) || !requireActiveAction(player)) return;
        long now = Instant.now().toEpochMilli();
        long previous = lastSneakAtEpochMs.getOrDefault(player.getUniqueId(), 0L);
        lastSneakAtEpochMs.put(player.getUniqueId(), now);
        if (now - previous <= 300L) {
            executeDodge(player);
            lastSneakAtEpochMs.put(player.getUniqueId(), 0L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cancelQuickUse(player, "접속 종료로 사용 취소");
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || !("DOWNED_GRACE".equals(state.lifeState) || "DOWNED".equals(state.lifeState)
                || "BEING_REVIVED".equals(state.lifeState))) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) return;
            RunSnapshot.PlayerState current = runs.playerState(player.getUniqueId()).orElse(null);
            if (current != null && ("DOWNED_GRACE".equals(current.lifeState) || "DOWNED".equals(current.lifeState)
                    || "BEING_REVIVED".equals(current.lifeState))) {
                completeDeath(player, "DOWNED_DISCONNECT");
            }
        }, 1L);
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
        if (!("DOWNED".equals(targetState.lifeState) || "BEING_REVIVED".equals(targetState.lifeState))) {
            return;
        }
        if (!reviver.isSneaking() || !validReviveGeometry(reviver, target)) {
            return;
        }
        event.setCancelled(true);
        if (runs.playerState(reviver.getUniqueId()).map(state -> state.ap < 5.0).orElse(true)) {
            ActionBarService.notice(reviver, Component.text("구조 시작에는 AP 5 이상이 필요합니다.", NamedTextColor.RED), 40);
            return;
        }
        long now = Instant.now().toEpochMilli();
        ReviveSession session = reviveSessions.computeIfAbsent(target.getUniqueId(),
                ignored -> new ReviveSession(target.getUniqueId(), now));
        if (!session.contributors.containsKey(reviver.getUniqueId()) && session.contributors.size() >= 2) {
            ActionBarService.notice(reviver, Component.text("이 대상은 이미 2명이 구조 중입니다.", NamedTextColor.RED), 40);
            return;
        }
        session.contributors.computeIfAbsent(reviver.getUniqueId(), ignored ->
                new ReviveContributor(reviver.getUniqueId(), now, reviver.getLocation().clone()));
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(target.getUniqueId().toString());
            state.lifeState = "BEING_REVIVED";
            state.reviveLastContributionAtEpochMs = now;
            state.reviveContributions.putIfAbsent(reviver.getUniqueId().toString(), 0.0);
        });
        reviver.sendMessage(ChatColor.YELLOW + target.getName() + " 구조 참여 — " + session.contributors.size() + "/2명");
        ActionBarService.critical(reviver, Component.text(target.getName() + " 구조 시작", NamedTextColor.YELLOW), 30);
        ActionBarService.critical(target, Component.text(reviver.getName() + "이(가) 구조 중", NamedTextColor.YELLOW), 30);
    }

    private CombatInputPolicy.LeftDisposition routeLeft(Player player) {
        if (!inCombatStance(player)) return CombatInputPolicy.LeftDisposition.VANILLA;
        long tick = Bukkit.getCurrentTick();
        if (cancelQuickUse(player, "좌클릭으로 사용 취소")) {
            lastLeftInputTick.put(player.getUniqueId(), tick);
            lastLeftDisposition.put(player.getUniqueId(), CombatInputPolicy.LeftDisposition.QUICK_ITEM_CANCELLED);
            return CombatInputPolicy.LeftDisposition.QUICK_ITEM_CANCELLED;
        }
        if (lastLeftInputTick.getOrDefault(player.getUniqueId(), Long.MIN_VALUE) == tick) {
            return lastLeftDisposition.getOrDefault(player.getUniqueId(), CombatInputPolicy.LeftDisposition.BASIC_ATTACK);
        }
        lastLeftInputTick.put(player.getUniqueId(), tick);
        String weaponId = equipment.resolveWeaponId(player);
        double targetRange = content.weapon(weaponId).range();
        CombatInputPolicy.LeftDisposition disposition = CombatInputPolicy.leftClick(
                player.getInventory().getHeldItemSlot(), isActionRestricted(player), player.isSneaking(), weaponId,
                nearestTarget(player, targetRange).isPresent(), player.getTargetBlockExact(4) != null);
        lastLeftDisposition.put(player.getUniqueId(), disposition);
        switch (disposition) {
            case BASIC_ATTACK -> executeBasicAttack(player);
            case WEAPON_SKILL -> executeWeaponActive(player, 2);
            case RESTRICTED -> showRestrictedAction(player);
            case QUICK_ITEM_CANCELLED -> { }
            case VANILLA, VANILLA_MINING -> { }
        }
        if (disposition != CombatInputPolicy.LeftDisposition.VANILLA
                && disposition != CombatInputPolicy.LeftDisposition.VANILLA_MINING) returnToCombatStance(player);
        return disposition;
    }

    private boolean executeBasicAttack(Player player) {
        if (statuses.blocksWeaponAttack(player)) {
            showControlRestriction(player, "제어/무장 해제 — 무기 공격 불가");
            return false;
        }
        String weaponId = equipment.resolveWeaponId(player);
        if (!requireUsableWeapon(player)) return false;
        PrototypeContent.WeaponDefinition weapon = contentWeapon(player, weaponId);
        PrototypeContent.SkillDefinition basic = skills.basicAttack(weaponId);
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        if ("TRIDENT".equals(weaponId) && !"HELD".equals(state.tridentState)) {
            ActionBarService.notice(player, Component.text("삼지창을 먼저 회수하세요 (Shift+R)", NamedTextColor.RED), 40);
            return false;
        }
        long now = System.nanoTime();
        if (attackReadyAtNanos.getOrDefault(player.getUniqueId(), 0L) > now) {
            return false;
        }
        if (usesArrowAmmo(weaponId) && !hasArrowAmmo(player, weaponId)) {
            ActionBarService.notice(player, Component.text("화살이 필요합니다", NamedTextColor.RED), 30);
            return false;
        }
        double apCost = growth.basicAttackApCost(player, weaponId, basic == null ? 0.0 : basic.apCost());
        if (!runs.consumeAp(player, apCost)) {
            apFailure(player, apCost);
            return false;
        }
        double cooldownMultiplier = runs.isTestRun()
                ? clamp(state.testCooldownMultiplier, 0.05, 10.0) : 1.0;
        int intervalTicks = basic == null ? weapon.intervalTicks() : skills.cooldownTicks(basic.id());
        attackReadyAtNanos.put(player.getUniqueId(), now
                + CombatMath.cooldownTicks(intervalTicks, cooldownMultiplier) * 50_000_000L);
        ComboState combo = combos.computeIfAbsent(player.getUniqueId(), ignored -> new ComboState());
        if (!weaponId.equals(combo.weaponId) || Instant.now().toEpochMilli() - combo.lastAttackAtEpochMs > 1250L) {
            combo.weaponId = weaponId;
            combo.stage = 0;
        }
        int stage = combo.stage % weapon.attackCoefficients().size();
        combo.stage = (stage + 1) % weapon.attackCoefficients().size();
        combo.lastAttackAtEpochMs = Instant.now().toEpochMilli();
        String executionId = UUID.randomUUID().toString();
        if (usesArrowAmmo(weaponId)) {
            if (!consumeArrowAmmo(player, weaponId)) {
                attackReadyAtNanos.remove(player.getUniqueId());
                runs.mutate(run -> {
                    RunSnapshot.PlayerState mutable = run.players.get(player.getUniqueId().toString());
                    mutable.ap = Math.min(mutable.maxAp, mutable.ap + apCost);
                });
                ActionBarService.notice(player, Component.text("화살이 필요합니다", NamedTextColor.RED), 30);
                return false;
            }
            markCombatAction(player);
            float velocity = "CROSSBOW".equals(weaponId) ? 3.4f : 2.8f;
            Arrow arrow = player.getWorld().spawnArrow(player.getEyeLocation(), player.getEyeLocation().getDirection(), velocity, 0.0f);
            arrow.setShooter(player);
            arrow.setDamage(0.0);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.getPersistentDataContainer().set(projectileOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            arrow.getPersistentDataContainer().set(projectileWeaponKey, PersistentDataType.STRING, weaponId);
            equipment.consumeMainWeaponDurability(player, 1, "BASIC_ATTACK");
            player.getWorld().playSound(player.getLocation(), "CROSSBOW".equals(weaponId)
                    ? Sound.ITEM_CROSSBOW_SHOOT : Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.1f);
            return true;
        }
        markCombatAction(player);
        equipment.consumeMainWeaponDurability(player, 1, "BASIC_ATTACK");
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
        if (!requireActiveAction(player) || !requireUsableWeapon(player)) return false;
        if (statuses.blocksWeaponSkill(player)) {
            showControlRestriction(player, "제어/무장 해제/침묵 — 무기 스킬 불가");
            return false;
        }
        String weaponId = equipment.resolveWeaponId(player);
        PrototypeContent.SkillDefinition skill = skills.resolve(player, slot);
        if (skill == null) {
            ActionBarService.notice(player, Component.text("W" + slot + " 스킬이 비어 있습니다. Shift+F → 스킬에서 장착하세요.", NamedTextColor.RED), 50);
            return false;
        }
        if (!requireSkillReady(player, skill)) return false;
        double apCost = growth.skillApCost(player, skill.id(), skill.apCost());
        if ("TRIDENT_TOGGLE".equals(skill.effect())) {
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
            boolean success = "HELD".equals(state.tridentState)
                    ? throwTrident(player, apCost)
                    : recallTrident(player, growth.skillApCost(player, skill.id(), Math.min(20.0, skill.apCost())));
            if (success) {
                markCombatAction(player);
                startSkillCooldown(player, skill);
                equipment.consumeMainWeaponDurability(player, 1, "SKILL:" + skill.id());
                showSkillEffect(player, skill, List.of());
            }
            return success;
        }
        if ("RELOAD".equals(skill.effect())) {
            boolean ledgerAmmo = ammoService != null && ammoService.generalArrowBalance(player) >= 2;
            if (!ledgerAmmo && !hasMaterial(player, Material.ARROW, 2)) {
                ActionBarService.notice(player, Component.text("순간 장전에는 화살 2개가 필요합니다", NamedTextColor.RED), 30);
                return false;
            }
            if (!runs.consumeAp(player, apCost)) {
                apFailure(player, apCost);
                return false;
            }
            if (ledgerAmmo) ammoService.consumeGeneralArrows(player, 2);
            else takeMaterial(player, Material.ARROW, 2);
            markCombatAction(player);
            runs.mutate(run -> {
                RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                state.crossbowLoadedAmmo = Math.min(6, state.crossbowLoadedAmmo + 2);
            });
            startSkillCooldown(player, skill);
            showSkillEffect(player, skill, List.of());
            ActionBarService.notice(player, Component.text("순간 장전 · 탄창 +2", NamedTextColor.AQUA), 30);
            return true;
        }
        if (usesArrowAmmo(weaponId) && !hasArrowAmmo(player, weaponId)) {
            ActionBarService.notice(player, Component.text("화살이 필요합니다", NamedTextColor.RED), 30);
            return false;
        }
        if (!runs.consumeAp(player, apCost)) {
            apFailure(player, apCost);
            return false;
        }
        if (usesArrowAmmo(weaponId)) consumeArrowAmmo(player, weaponId);
        markCombatAction(player);
        startSkillCooldown(player, skill);
        equipment.consumeMainWeaponDurability(player, 1, "SKILL:" + skill.id());
        List<LivingEntity> targets = coneTargets(player, skill.range(), skill.arcDegrees(), skill.maxTargets());
        String executionId = "skill:" + skill.id() + ":" + UUID.randomUUID();
        for (LivingEntity target : targets) {
            damageCombatEntity(player, target, 100.0 * skill.damageCoefficient(), skill.breakDamage(),
                    executionId + ":" + target.getUniqueId());
            applySkillEffect(player, target, skill, executionId);
        }
        applyCasterSkillEffect(player, skill, targets);
        showSkillEffect(player, skill, targets);
        runs.mutate(run -> run.players.get(player.getUniqueId().toString()).tutorialSignals.add("USED_SKILL"));
        ActionBarService.notice(player, Component.text("W" + slot + " " + skill.name() + " / AP -" + Math.round(apCost), NamedTextColor.AQUA), 30);
        return true;
    }

    private void executeCommonActive(Player player, int slot) {
        if (!requireActiveAction(player)) return;
        if (statuses.blocksCommonSkill(player)) {
            showControlRestriction(player, "제어/침묵 — 공용 액티브 불가");
            return;
        }
        PrototypeContent.SkillDefinition skill = skills.resolveCommon(player, slot);
        if (skill == null) {
            ActionBarService.notice(player, Component.text("C" + slot + " 공용 액티브가 비어 있습니다.", NamedTextColor.RED), 40);
            return;
        }
        if (!requireSkillReady(player, skill)) return;
        boolean enemyTargetRequired = "MARK".equals(skill.effect()) || skill.id().contains("break_call");
        LivingEntity target = enemyTargetRequired ? nearestTarget(player, skill.range()).orElse(null)
                : "RESCUE_PULL".equals(skill.effect()) ? nearestDowned(player).orElse(null) : null;
        if (enemyTargetRequired && target == null) {
            ActionBarService.notice(player, Component.text("사거리 안에 대상이 없습니다", NamedTextColor.RED), 30);
            return;
        }
        if ("RESCUE_PULL".equals(skill.effect()) && target == null) {
            ActionBarService.notice(player, Component.text("8블록 안에 빈사 파티원이 없습니다", NamedTextColor.GRAY), 30);
            return;
        }
        String consumableId = skills.consumableId(skill.id());
        if (!consumableId.isBlank() && !equipment.hasRegisteredItem(player, consumableId)) {
            ActionBarService.notice(player, Component.text("필요 소모품이 없습니다: " + consumableId, NamedTextColor.RED), 40);
            return;
        }
        if (!consumableId.isBlank() && !canUseLimitedConsumable(player, consumableId)) return;
        double apCost = growth.skillApCost(player, skill.id(), skill.apCost());
        if (!runs.consumeAp(player, apCost)) {
            apFailure(player, apCost);
            return;
        }
        if (!consumableId.isBlank() && !equipment.consumeQuickItem(player, consumableId)) {
            runs.mutate(run -> {
                RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                state.ap = Math.min(state.maxAp, state.ap + apCost);
            });
            return;
        }
        if (!consumableId.isBlank()) recordConsumableUse(player, consumableId);
        markCombatAction(player);
        startSkillCooldown(player, skill);
        executeCommonEffect(player, skill, target);
        showSkillEffect(player, skill, target == null ? List.of() : List.of(target));
        ActionBarService.notice(player, Component.text("C" + slot + " " + skill.name()
                + " / AP -" + Math.round(apCost), NamedTextColor.LIGHT_PURPLE), 35);
    }

    private void executeQuickItem(Player player, int slot) {
        if (!requireActiveAction(player)) return;
        String bound = equipment.quickBinding(player, slot);
        if (bound == null || !equipment.hasRegisteredItem(player, bound)) {
            ActionBarService.notice(player, Component.text("Q" + slot + " 소모품 없음", NamedTextColor.RED), 30);
            return;
        }
        if (!quickEffectSupported(bound)) {
            ActionBarService.notice(player, Component.text("아직 직접 사용할 수 없는 소모품입니다: " + bound, NamedTextColor.RED), 40);
            return;
        }
        if ("WSI-CONS-REPAIR_KIT".equals(bound) && !equipment.canRepairEquipped(player)) {
            ActionBarService.notice(player, Component.text("수리가 필요한 장착 장비가 없습니다", NamedTextColor.GRAY), 35);
            return;
        }
        if ("WSI-CONS-PORTABLE_PURIFIER_CHARGE".equals(bound)
                && (facilityService == null || !facilityService.hasActivePortablePurifier(player))) {
            ActionBarService.notice(player, Component.text("가동할 FAC-P05 휴대 정화기가 없습니다", NamedTextColor.RED), 40);
            return;
        }
        if (!canApplyQuickItem(player, bound)) return;
        if ("WSI-CONS-RATION_PACK".equals(bound)) {
            beginRationUse(player, slot, bound);
            return;
        }
        if (!canUseLimitedConsumable(player, bound) || !equipment.consumeQuickItem(player, bound)) return;
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
            case "WSI-CONS-RATION_PACK" -> {
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + 8));
                player.setSaturation(Math.min(20.0f, player.getSaturation() + 6.0f));
                result = "허기 +8 / 포화 +6";
            }
            case "WSI-CONS-BANDAGE" -> { removePlayerStatus(player, "bleed", 1); result = "BLEED 1중첩 제거"; }
            case "WSI-CONS-REPAIR_KIT" -> {
                equipment.repairMostDamagedWithConsumedKit(player); result = "가장 손상된 장착 장비 40% 수리";
            }
            case "WSI-CONS-PURIFY_AMPOULE" -> {
                cleanseWeakEffects(player); result = "개인 오염 감소 / 약한 상태 정화";
            }
            case "WSI-CONS-AP_STIM" -> {
                runs.mutate(run -> {
                    RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                    state.ap = Math.min(state.maxAp, state.ap + 30.0);
                });
                result = "AP +30";
            }
            case "WSI-CONS-RESCUE_BRACE" -> {
                runs.mutate(run -> run.players.get(player.getUniqueId().toString()).rescueBraceCharges++);
                result = "다음 구조 중단 저항 1회";
            }
            case "WSI-CONS-PORTABLE_PURIFIER_CHARGE" -> {
                facilityService.extendPortablePurifier(player, 60_000L);
                result = "FAC-P05 가동시간 +60초";
            }
            case "WSI-CONS-ANTIDOTE_INJECTION" -> {
                int removed = statuses.remove(player, "POISON",
                        ConsumableRuntimePolicy.removableStacks(bound));
                if (removed == 0) player.removePotionEffect(PotionEffectType.POISON);
                result = "POISON " + Math.max(1, removed) + "중첩 제거";
            }
            case "WSI-CONS-COOLING_SALVE" -> {
                int removed = statuses.remove(player, "BURN",
                        ConsumableRuntimePolicy.removableStacks(bound));
                if (removed == 0) player.setFireTicks(0);
                statuses.reduceBurnDuration(player, ConsumableRuntimePolicy.COOLING_BURN_DURATION_MULTIPLIER,
                        ConsumableRuntimePolicy.COOLING_PROTECTION_MILLIS);
                result = "BURN " + removed + "중첩 제거 / 5초 지속시간 -25%";
            }
            case "WSI-CONS-TOURNIQUET" -> { removePlayerStatus(player, "bleed", 2); result = "BLEED 2중첩 제거"; }
            case "WSI-CONS-NEURAL_STABILIZER" -> {
                String removed = ConsumableRuntimePolicy.neuralCleansePriority().stream()
                        .filter(id -> statuses.active(player, id)).findFirst().orElseThrow();
                statuses.remove(player, removed, 1);
                result = removed + " 1개 제거";
            }
            case "WSI-CONS-REINFORCED_RESCUE_BRACE" -> {
                runs.mutate(run -> {
                    RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                    state.rescueBraceCharges++;
                    state.rescueInterruptThresholdBonus = Math.max(state.rescueInterruptThresholdBonus, 0.25);
                });
                result = "다음 구조 중단 임계 +25%";
            }
            case "WSI-CONS-BIO_SHIELD_AMPOULE" -> {
                double shield = maxHealth * ConsumableRuntimePolicy.BIO_SHIELD_MAX_HP_FRACTION;
                double granted = Math.max(0.0, shield - player.getAbsorptionAmount());
                player.setAbsorptionAmount(player.getAbsorptionAmount() + granted);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && granted > 0.0) {
                        player.setAbsorptionAmount(Math.max(0.0, player.getAbsorptionAmount() - granted));
                    }
                }, ConsumableRuntimePolicy.BIO_SHIELD_DURATION_TICKS);
                result = "최대 HP 8% 보호막";
            }
            default -> { ActionBarService.notice(player, Component.text("지원하지 않는 Q 아이템 " + bound, NamedTextColor.RED), 40); return; }
        }
        recordConsumableUse(player, bound);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.0f);
        ProductionContentCatalog.ItemEntry definition = production.nonEquipmentItemsById().get(bound);
        String name = definition == null ? content.item(bound).name() : definition.name();
        ActionBarService.notice(player, Component.text("Q" + slot + " " + name + ": " + result, NamedTextColor.GREEN), 40);
    }

    private boolean quickEffectSupported(String id) {
        return Set.of("RATION", "BANDAGE", "ANTIDOTE", "WSI-CONS-RATION_PACK", "WSI-CONS-BANDAGE",
                "WSI-CONS-REPAIR_KIT", "WSI-CONS-PURIFY_AMPOULE", "WSI-CONS-AP_STIM",
                "WSI-CONS-RESCUE_BRACE", "WSI-CONS-PORTABLE_PURIFIER_CHARGE",
                "WSI-CONS-ANTIDOTE_INJECTION", "WSI-CONS-COOLING_SALVE", "WSI-CONS-TOURNIQUET",
                "WSI-CONS-NEURAL_STABILIZER", "WSI-CONS-REINFORCED_RESCUE_BRACE",
                "WSI-CONS-BIO_SHIELD_AMPOULE").contains(id);
    }

    private void beginRationUse(Player player, int slot, String itemId) {
        if (quickUseChannels.containsKey(player.getUniqueId())) {
            ActionBarService.notice(player, Component.text("이미 소모품을 사용 중입니다", NamedTextColor.RED), 30);
            return;
        }
        if (!equipment.consumeQuickItem(player, itemId)) return;
        boolean inCombat = CombatUseScopePolicy.sharedScope(runs.current().orElseThrow()).isPresent()
                || runs.playerState(player.getUniqueId()).map(state ->
                state.personalCombatScopeExpiresAtEpochMs >= runs.clockNowMillis()).orElse(false);
        long durationTicks = ConsumableRuntimePolicy.rationUseTicks(inCombat);
        long startedTick = Bukkit.getCurrentTick();
        quickUseChannels.put(player.getUniqueId(), new QuickUseChannel(itemId, slot, startedTick,
                startedTick + durationTicks));
        ActionBarService.critical(player, Component.text("Q" + slot + " 야전 배급팩 섭취 시작 · "
                + durationTicks / 20 + "초 · 좌클릭 취소", NamedTextColor.YELLOW), 40);
    }

    private void processQuickUseChannels() {
        if (quickUseChannels.isEmpty()) return;
        long tick = Bukkit.getCurrentTick();
        for (Map.Entry<UUID, QuickUseChannel> entry : new ArrayList<>(quickUseChannels.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            QuickUseChannel channel = entry.getValue();
            if (player == null || !player.isOnline()) {
                quickUseChannels.remove(entry.getKey());
                continue;
            }
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
            if (!runs.isRunningMember(player) || state == null || !"ACTIVE".equals(state.lifeState)) {
                cancelQuickUse(player, "행동 불가로 사용 취소");
                continue;
            }
            if (tick >= channel.completesAtTick) {
                quickUseChannels.remove(player.getUniqueId());
                if (!canApplyQuickItem(player, channel.itemId)) {
                    equipment.grantQuickItem(player, channel.itemId, 1);
                    continue;
                }
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + 8));
                player.setSaturation(Math.min(20.0f, player.getSaturation() + 6.0f));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.0f);
                ActionBarService.critical(player, Component.text("Q" + channel.slot
                        + " 야전 배급팩: 허기 +8 / 포화 +6", NamedTextColor.GREEN), 40);
                continue;
            }
            if ((tick - channel.startedAtTick) % 10L == 0L) {
                long elapsed = tick - channel.startedAtTick;
                long total = channel.completesAtTick - channel.startedAtTick;
                int percent = (int) Math.min(99L, elapsed * 100L / Math.max(1L, total));
                ActionBarService.critical(player, Component.text("야전 배급팩 섭취 " + percent
                        + "% · 좌클릭 취소", NamedTextColor.YELLOW), 12);
            }
        }
    }

    private boolean cancelQuickUse(Player player, String reason) {
        QuickUseChannel cancelled = quickUseChannels.remove(player.getUniqueId());
        if (cancelled == null) return false;
        equipment.grantQuickItem(player, cancelled.itemId, 1);
        ActionBarService.critical(player, Component.text(reason + " · 예약 수량 반환", NamedTextColor.RED), 35);
        return true;
    }

    public void shutdown() {
        for (UUID playerId : new HashSet<>(quickUseChannels.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) cancelQuickUse(player, "서버 종료로 사용 취소");
        }
        quickUseChannels.clear();
    }

    private boolean canUseLimitedConsumable(Player player, String id) {
        int limit = ConsumableRuntimePolicy.combatLimit(id);
        if (limit == Integer.MAX_VALUE) return true;
        String scope = combatUseScope(player, false);
        int used = runs.playerState(player.getUniqueId()).map(state -> state.quickItemUsesByCombat == null ? 0
                : state.quickItemUsesByCombat.getOrDefault(scope + ":" + id, 0)).orElse(0);
        if (used < limit) return true;
        ActionBarService.notice(player, Component.text("현재 전투 사용 한도에 도달했습니다: " + id,
                NamedTextColor.RED), 40);
        return false;
    }

    private void recordConsumableUse(Player player, String id) {
        if (!id.startsWith("WSI-CONS-")) return;
        String scope = combatUseScope(player, true);
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
            if (state.quickItemUsesByCombat == null) state.quickItemUsesByCombat = new HashMap<>();
            state.quickItemUsesByCombat.merge(scope + ":" + id, 1, Integer::sum);
        });
    }

    private boolean canApplyQuickItem(Player player, String id) {
        boolean allowed = switch (id) {
            case "WSI-CONS-RATION_PACK" -> player.getFoodLevel() < 20;
            case "WSI-CONS-BANDAGE", "WSI-CONS-TOURNIQUET" -> statuses.active(player, "BLEED");
            case "WSI-CONS-ANTIDOTE_INJECTION" -> statuses.active(player, "POISON")
                    || player.hasPotionEffect(PotionEffectType.POISON);
            case "WSI-CONS-NEURAL_STABILIZER" -> {
                if (!ConsumableRuntimePolicy.neuralSelfUseAllowed(statuses.hardControlled(player))) {
                    ActionBarService.notice(player, Component.text("HARD_CC 중에는 자신에게 사용할 수 없습니다",
                            NamedTextColor.RED), 40);
                    yield false;
                }
                yield ConsumableRuntimePolicy.neuralCleansePriority().stream()
                        .anyMatch(status -> statuses.active(player, status));
            }
            default -> true;
        };
        if (!allowed && !"WSI-CONS-NEURAL_STABILIZER".equals(id)) {
            ActionBarService.notice(player, Component.text("현재 적용할 수 없는 소모품입니다", NamedTextColor.GRAY), 35);
        } else if (!allowed && !statuses.hardControlled(player)) {
            ActionBarService.notice(player, Component.text("제거할 ROOT·SILENCE·DISARM이 없습니다",
                    NamedTextColor.GRAY), 35);
        }
        return allowed;
    }

    private String combatUseScope(Player player, boolean commitPersonalScope) {
        RunSnapshot run = runs.current().orElseThrow();
        java.util.Optional<String> shared = CombatUseScopePolicy.sharedScope(run);
        if (shared.isPresent()) return shared.get();
        RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
        CombatUseScopePolicy.PersonalScope scope = CombatUseScopePolicy.personalScope(
                state.personalCombatSequence, state.personalCombatScopeExpiresAtEpochMs, runs.clockNowMillis());
        if (commitPersonalScope && scope.newlyOpened()) {
            runs.mutate(snapshot -> {
                RunSnapshot.PlayerState mutable = snapshot.players.get(player.getUniqueId().toString());
                mutable.personalCombatSequence = scope.sequence();
                mutable.personalCombatScopeExpiresAtEpochMs = scope.expiresAtEpochMs();
            });
        }
        return scope.key(run.runId);
    }

    private void markCombatAction(Player player) {
        RunSnapshot run = runs.current().orElse(null);
        if (run == null || CombatUseScopePolicy.sharedScope(run).isPresent()) return;
        RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
        if (state == null) return;
        CombatUseScopePolicy.PersonalScope scope = CombatUseScopePolicy.onCombatAction(
                state.personalCombatSequence, state.personalCombatScopeExpiresAtEpochMs, runs.clockNowMillis());
        runs.mutate(snapshot -> {
            RunSnapshot.PlayerState mutable = snapshot.players.get(player.getUniqueId().toString());
            mutable.personalCombatSequence = scope.sequence();
            mutable.personalCombatScopeExpiresAtEpochMs = scope.expiresAtEpochMs();
        });
    }

    private void removePlayerStatus(Player player, String id, int maximumStacks) {
        statuses.remove(player, id, maximumStacks);
        player.getPersistentDataContainer().remove(new NamespacedKey(plugin, "status_" + id));
    }

    private void executeDodge(Player player) {
        if (statuses.blocksDodge(player)) {
            showControlRestriction(player, "제어 상태 — 회피 불가");
            return;
        }
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        double equipmentEvasion = Math.max(0.0, equipment.activeStat(player, "EVA"));
        double equipmentAdjustedCost = Math.max(10.0,
                PlayerStatPolicy.dodgeCost(state.investedStats) - Math.floor(equipmentEvasion / 3.0));
        double cost = growth.dodgeCost(player, equipmentAdjustedCost);
        if (!runs.consumeAp(player, cost)) {
            apFailure(player, cost);
            return;
        }
        markCombatAction(player);
        growth.onDodgeStarted(player, cost);
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        if (player.isSneaking()) {
            direction.multiply(-1.0);
        }
        double equipmentDistance = 1.0 + Math.min(0.30, equipmentEvasion * 0.01);
        player.setVelocity(direction.multiply(0.9 * PlayerStatPolicy.dodgeDistanceMultiplier(state.investedStats)
                * equipmentDistance).setY(0.12));
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
        if (DeathRuntimePolicy.fatalInsteadOfDowned(state.injuryStacks)) {
            completeDeath(player, "MAX_INJURY_FATAL");
            return;
        }
        long now = Instant.now().toEpochMilli();
        int nextInjury = state.injuryStacks + 1;
        double maximumHealth = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0
                : player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double downedMaximum = maximumHealth * DeathRuntimePolicy.downedHealthFraction(nextInjury);
        runs.mutate(run -> {
            RunSnapshot.PlayerState value = run.players.get(player.getUniqueId().toString());
            value.lifeState = "DOWNED_GRACE";
            value.downedAtEpochMs = now;
            value.downedGraceUntilEpochMs = now + DeathRuntimePolicy.DOWNED_GRACE_MILLIS;
            value.downedMaxHp = downedMaximum;
            value.downedHp = downedMaximum;
            value.injuryStacks = nextInjury;
            value.reviveProgress = 0.0;
            value.reviveLastContributionAtEpochMs = 0L;
            value.reviveContributions.clear();
            value.reviveProtectionUntilEpochMs = 0L;
            value.reviveTailProtectionUntilEpochMs = 0L;
            value.ap = 0.0;
        });
        invulnerableUntilEpochMs.remove(player.getUniqueId());
        cancelAllReviveParticipation(player.getUniqueId(), "구조자 빈사");
        reviveSessions.remove(player.getUniqueId());
        statuses.clearControls(player);
        player.setHealth(1.0);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
        if (player.isInsideVehicle()) player.leaveVehicle();
        applyLifeMovementProfile(player, "DOWNED_GRACE");
        runs.broadcast(ChatColor.RED + "[빈사 보호] " + player.getName() + " — 부상 " + nextInjury
                + "/3 · " + DeathRuntimePolicy.DOWNED_GRACE_MILLIS / 1000.0 + "초");
        for (Player member : runs.onlineMembers()) {
            ActionBarService.critical(member, Component.text("[빈사] " + player.getName() + " — 웅크리고 우클릭하여 구조", NamedTextColor.RED), 80);
        }
        telemetry.event(runs.current().orElseThrow().runId, "PLAYER_STATE_CHANGED", "{\"state\":\"DOWNED\"}");
    }

    private void processRevives(long now) {
        for (Player target : runs.onlineMembers()) {
            RunSnapshot.PlayerState state = runs.playerState(target.getUniqueId()).orElseThrow();
            if ("BEING_REVIVED".equals(state.lifeState)) {
                reviveSessions.computeIfAbsent(target.getUniqueId(), ignored ->
                        new ReviveSession(target.getUniqueId(), now));
            }
        }
        List<UUID> removeSessions = new ArrayList<>();
        for (ReviveSession session : reviveSessions.values()) {
            Player target = Bukkit.getPlayer(session.target);
            RunSnapshot.PlayerState targetState = runs.playerState(session.target).orElse(null);
            if (target == null || targetState == null || !("DOWNED".equals(targetState.lifeState)
                    || "BEING_REVIVED".equals(targetState.lifeState))) {
                removeSessions.add(session.target);
                continue;
            }
            long elapsed = Math.max(0L, Math.min(250L, now - session.lastTickAtEpochMs));
            session.lastTickAtEpochMs = now;
            List<ReviveContributor> ordered = session.contributors.values().stream()
                    .sorted(Comparator.comparingLong(value -> value.startedAtEpochMs)).toList();
            List<UUID> invalid = new ArrayList<>();
            double weightedSpeed = 0.0;
            List<Player> activeContributors = new ArrayList<>();
            Map<UUID, Double> contributionWeights = new HashMap<>();
            for (int index = 0; index < ordered.size(); index++) {
                ReviveContributor contributor = ordered.get(index);
                Player reviver = Bukkit.getPlayer(contributor.playerUuid);
                if (!validReviveContributor(reviver, target, contributor)) {
                    invalid.add(contributor.playerUuid);
                    continue;
                }
                double apCost = DeathRuntimePolicy.reviveApCost(elapsed);
                if (apCost > 0.0 && !runs.consumeAp(reviver, apCost)) {
                    invalid.add(contributor.playerUuid);
                    ActionBarService.critical(reviver, Component.text("AP 부족으로 구조 중단", NamedTextColor.RED), 40);
                    continue;
                }
                runs.mutateTransient(run -> run.players.get(reviver.getUniqueId().toString())
                        .apRegenBlockedUntilEpochMs = now + 1_000L);
                double speed = growth.reviveSpeedMultiplier(reviver) * DeathRuntimePolicy.contributorWeight(index);
                RunSnapshot run = runs.current().orElseThrow();
                if (FacilityStateAccess.activeNear(run, "FAC-P07", target.getWorld().getName(),
                        target.getX(), target.getY(), target.getZ(), 12.0)) speed *= 1.15;
                weightedSpeed += speed;
                activeContributors.add(reviver);
                contributionWeights.put(reviver.getUniqueId(), speed);
            }
            invalid.forEach(session.contributors::remove);
            double previousProgress = targetState.reviveProgress;
            double nextProgress = previousProgress;
            if (!activeContributors.isEmpty()) {
                double delta = DeathRuntimePolicy.reviveProgressDelta(
                        Math.max(1, targetState.injuryStacks), elapsed, weightedSpeed);
                nextProgress = Math.min(1.0, previousProgress + delta);
                double creditedProgress = nextProgress - previousProgress;
                double totalWeight = weightedSpeed;
                double persistedProgress = nextProgress;
                runs.mutateTransient(run -> {
                    RunSnapshot.PlayerState value = run.players.get(session.target.toString());
                    value.lifeState = "BEING_REVIVED";
                    value.reviveProgress = persistedProgress;
                    value.reviveLastContributionAtEpochMs = now;
                    for (Player contributor : activeContributors) {
                        double share = contributionWeights.getOrDefault(contributor.getUniqueId(), 0.0) / totalWeight;
                        value.reviveContributions.merge(contributor.getUniqueId().toString(),
                                creditedProgress * share, Double::sum);
                    }
                });
            } else if (now - targetState.reviveLastContributionAtEpochMs > 500L) {
                nextProgress = Math.max(0.0, previousProgress - DeathRuntimePolicy.reviveProgressDecay(elapsed));
                double persistedProgress = nextProgress;
                runs.mutateTransient(run -> run.players.get(session.target.toString()).reviveProgress = persistedProgress);
                if (nextProgress <= 0.0) {
                    runs.mutate(run -> run.players.get(session.target.toString()).lifeState = "DOWNED");
                    removeSessions.add(session.target);
                    continue;
                }
            }
            int percent = (int) Math.round(nextProgress * 100.0);
            String progressText = "구조 " + percent + "% · " + activeContributors.size() + "/2명";
            ActionBarService.show(target, Component.text(progressText, NamedTextColor.AQUA), 3, 100);
            for (Player contributor : activeContributors) {
                ActionBarService.show(contributor, Component.text(target.getName() + " " + progressText,
                        NamedTextColor.AQUA), 3, 100);
            }
            if (nextProgress >= 1.0) {
                revive(target, activeContributors);
                removeSessions.add(session.target);
            }
        }
        removeSessions.forEach(reviveSessions::remove);
    }

    private void revive(Player target, List<Player> contributors) {
        RunSnapshot.PlayerState before = runs.playerState(target.getUniqueId()).orElseThrow();
        Player primary = contributors.stream().max(Comparator.comparingDouble(player ->
                before.reviveContributions.getOrDefault(player.getUniqueId().toString(), 0.0))).orElse(null);
        long now = Instant.now().toEpochMilli();
        runs.mutate(run -> {
            RunSnapshot.PlayerState state = run.players.get(target.getUniqueId().toString());
            state.lifeState = "ACTIVE";
            state.downedAtEpochMs = 0L;
            state.downedGraceUntilEpochMs = 0L;
            state.downedHp = 0.0;
            state.downedMaxHp = 0.0;
            state.reviveProgress = 0.0;
            state.reviveLastContributionAtEpochMs = 0L;
            state.reviveProtectionUntilEpochMs = now + 2_000L;
            state.reviveTailProtectionUntilEpochMs = now + 3_000L;
            state.ap = state.maxAp * 0.20;
        });
        target.removePotionEffect(PotionEffectType.SLOWNESS);
        target.removePotionEffect(PotionEffectType.GLOWING);
        restoreActiveMovementProfile(target);
        statuses.remove(target, "BURN", 64);
        statuses.remove(target, "POISON", 64);
        statuses.remove(target, "BLEED", 64);
        target.removePotionEffect(PotionEffectType.POISON);
        target.removePotionEffect(PotionEffectType.WITHER);
        target.setFireTicks(0);
        RunSnapshot.PlayerState revived = runs.playerState(target.getUniqueId()).orElseThrow();
        double maximum = target.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0
                : target.getAttribute(Attribute.MAX_HEALTH).getValue();
        target.setHealth(Math.max(1.0, maximum * DeathRuntimePolicy.reviveHealthFraction(
                Math.max(1, revived.injuryStacks))));
        if (primary != null) growth.onReviveCompleted(primary, target);
        String rescuerNames = contributors.isEmpty() ? "파티" : contributors.stream().map(Player::getName)
                .collect(java.util.stream.Collectors.joining(", "));
        runs.broadcast(ChatColor.GREEN + rescuerNames + "이(가) " + target.getName() + "을 구조했습니다.");
        for (Player contributor : contributors) {
            ActionBarService.critical(contributor, Component.text(target.getName() + " 구조 완료", NamedTextColor.GREEN), 60);
        }
        ActionBarService.critical(target, Component.text("구조 완료 · 전투 복귀", NamedTextColor.GREEN), 60);
        telemetry.event(runs.current().orElseThrow().runId, "PLAYER_STATE_CHANGED", "{\"state\":\"ACTIVE\",\"reason\":\"REVIVED\",\"contributors\":"
                + contributors.size() + "}");
    }

    private boolean withinReviveRange(Player reviver, Player target) {
        return reviver.getWorld().equals(target.getWorld())
                && reviver.getLocation().distanceSquared(target.getLocation()) <= REVIVE_RANGE_SQUARED;
    }

    private boolean validReviveGeometry(Player reviver, Player target) {
        if (!withinReviveRange(reviver, target) || !reviver.hasLineOfSight(target)) return false;
        Vector direction = reviver.getEyeLocation().getDirection().normalize();
        Vector towardTarget = target.getEyeLocation().toVector().subtract(reviver.getEyeLocation().toVector());
        return towardTarget.lengthSquared() > 0.0001 && direction.dot(towardTarget.normalize()) >= 0.35;
    }

    private boolean validReviveContributor(Player reviver, Player target, ReviveContributor contributor) {
        if (reviver == null || !reviver.isOnline() || !runs.isRunningMember(reviver) || !reviver.isSneaking()) return false;
        if (!runs.playerState(reviver.getUniqueId()).map(state -> "ACTIVE".equals(state.lifeState)).orElse(false)
                || statuses.blocksAllActions(reviver)) return false;
        if (!validReviveGeometry(reviver, target)) return false;
        return contributor.anchor.getWorld() != null && contributor.anchor.getWorld().equals(reviver.getWorld())
                && contributor.anchor.distanceSquared(reviver.getLocation()) <= 0.75 * 0.75;
    }

    private void interruptReviveContributionOnDamage(Player player, double finalDamage) {
        if (!isReviving(player) || !Double.isFinite(finalDamage) || finalDamage <= 0.0) return;
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return;
        double maximum = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0
                : player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double threshold = 0.05 + (state.rescueBraceCharges > 0
                ? Math.max(0.0, state.rescueInterruptThresholdBonus) : 0.0);
        if (finalDamage + 1.0e-6 < maximum * threshold) return;
        if (state.rescueBraceCharges > 0) {
            runs.mutate(run -> {
                RunSnapshot.PlayerState current = run.players.get(player.getUniqueId().toString());
                current.rescueBraceCharges--;
                current.rescueInterruptThresholdBonus = 0.0;
            });
            ActionBarService.critical(player, Component.text("구조 보호대가 중단을 막았습니다.", NamedTextColor.YELLOW), 40);
            return;
        }
        cancelAllReviveParticipation(player.getUniqueId(), "강한 피해로 구조 중단");
    }

    private void cancelAllReviveParticipation(UUID contributorId, String reason) {
        Player contributor = Bukkit.getPlayer(contributorId);
        for (ReviveSession session : reviveSessions.values()) {
            if (session.contributors.remove(contributorId) != null && contributor != null) {
                ActionBarService.critical(contributor, Component.text(reason, NamedTextColor.RED), 40);
            }
        }
    }

    private boolean isReviving(Player player) {
        return reviveSessions.values().stream()
                .anyMatch(session -> session.contributors.containsKey(player.getUniqueId()));
    }

    private boolean reviveProtected(Player player) {
        for (ReviveSession session : reviveSessions.values()) {
            if (!session.target.equals(player.getUniqueId())
                    && !session.contributors.containsKey(player.getUniqueId())) continue;
            for (UUID contributorId : session.contributors.keySet()) {
                Player reviver = Bukkit.getPlayer(contributorId);
                if (reviver != null && growth.hasAugment(reviver, "AUG-P-014")) return true;
            }
        }
        return false;
    }

    private boolean isDamageOverTime(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.POISON
                || cause == EntityDamageEvent.DamageCause.WITHER;
    }

    private void processLifeStates(long now) {
        for (Player player : runs.onlineMembers()) {
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
            if ("DEAD_PENDING".equals(state.lifeState)) {
                completeDeath(player, "RECOVER_PENDING");
                continue;
            }
            if ("DOWNED_GRACE".equals(state.lifeState) && now >= state.downedGraceUntilEpochMs) {
                runs.mutate(run -> run.players.get(player.getUniqueId().toString()).lifeState = "DOWNED");
                ActionBarService.critical(player, Component.text("빈사 상태 · 구조가 필요합니다.", NamedTextColor.RED), 60);
            }
        }
        boolean deathTransactionPending = runs.current().stream().flatMap(run -> run.players.values().stream())
                .anyMatch(state -> "DEAD_PENDING".equals(state.lifeState));
        if (!deathTransactionPending && runs.current().map(run -> "RUNNING".equals(run.state)).orElse(false)
                && runs.survivableCount() == 0) {
            try {
                runs.stop("PARTY_WIPED", "system");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private void synchronizeLifeMovement() {
        Set<UUID> online = new HashSet<>();
        for (Player player : runs.onlineMembers()) {
            online.add(player.getUniqueId());
            String lifeState = runs.playerState(player.getUniqueId()).map(state -> state.lifeState).orElse("");
            if (isDowned(lifeState)) {
                applyLifeMovementProfile(player, lifeState);
                player.setSprinting(false);
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                if (player.isInsideVehicle()) player.leaveVehicle();
            } else if (lifeMovementProfiles.contains(player.getUniqueId())) {
                restoreActiveMovementProfile(player);
            }
        }
        lifeMovementProfiles.removeIf(uuid -> !online.contains(uuid));
    }

    private void applyLifeMovementProfile(Player player, String lifeState) {
        var speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.setBaseValue(activeMovementSpeed(player) * DeathRuntimePolicy.movementFraction(lifeState));
        lifeMovementProfiles.add(player.getUniqueId());
    }

    private void restoreActiveMovementProfile(Player player) {
        var speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(activeMovementSpeed(player));
        lifeMovementProfiles.remove(player.getUniqueId());
    }

    private double activeMovementSpeed(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return 0.11;
        return Math.min(0.16, PlayerStatPolicy.movementSpeed(state.investedStats)
                * (1.0 + equipment.activeStats(player).value("SPD") / 100.0));
    }

    private void enforceSpectatorBoundaries() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || !"RUNNING".equals(snapshot.state)) return;
        SpectatorAnchor arena = activeArenaAnchor(snapshot);
        List<SpectatorAnchor> normalAnchors = arena == null ? spectatorAnchors(snapshot) : List.of(arena);
        if (normalAnchors.isEmpty()) return;
        for (Player spectator : runs.onlineMembers()) {
            RunSnapshot.PlayerState state = runs.playerState(spectator.getUniqueId()).orElse(null);
            if (state == null || !"DEAD".equals(state.lifeState) || spectator.getGameMode() != GameMode.SPECTATOR) {
                continue;
            }
            Entity viewed = spectator.getSpectatorTarget();
            if (viewed instanceof Player viewedPlayer && normalAnchors.stream()
                    .anyMatch(anchor -> anchor.player != null && anchor.player.equals(viewedPlayer))) continue;
            if (normalAnchors.stream().anyMatch(anchor -> anchor.contains(spectator.getLocation()))) continue;
            SpectatorAnchor nearest = normalAnchors.stream().min(Comparator.comparingDouble(anchor ->
                    anchor.distanceSquared(spectator.getLocation()))).orElse(null);
            if (nearest == null) continue;
            if (nearest.player != null && nearest.player.isOnline()) {
                teleportRestricted(spectator, nearest.player.getLocation());
                spectator.setSpectatorTarget(nearest.player);
            } else {
                teleportRestricted(spectator, nearest.location.clone().add(0.0, 2.0, 0.0));
            }
            ActionBarService.critical(spectator,
                    Component.text("관전 허용 영역으로 복귀했습니다.", NamedTextColor.YELLOW), 50);
            telemetry.event(snapshot.runId, "SPECTATOR_BOUNDARY_RECOVERY",
                    "{\"player\":\"" + spectator.getUniqueId() + "\",\"radius\":" + nearest.radius + "}");
        }
    }

    private SpectatorAnchor activeArenaAnchor(RunSnapshot snapshot) {
        RunSnapshot.FinalState finale = snapshot.finalObjective;
        if (finale != null && finale.state != null && finale.state.startsWith("ACTIVE_STAGE_")
                && finale.arenaWorld != null) {
            org.bukkit.World world = Bukkit.getWorld(finale.arenaWorld);
            if (world != null) return new SpectatorAnchor(new Location(world, finale.arenaX, finale.arenaY, finale.arenaZ),
                    DeathRuntimePolicy.spectatorArenaRadius(finale.objectiveId), null);
        }
        RunSnapshot.BossState boss = snapshot.boss;
        if (boss == null || !"ACTIVE".equals(boss.state) || boss.world == null) return null;
        org.bukkit.World world = Bukkit.getWorld(boss.world);
        if (world == null) return null;
        return new SpectatorAnchor(new Location(world, boss.x, boss.y, boss.z),
                DeathRuntimePolicy.spectatorArenaRadius(boss.bossId), null);
    }

    private List<SpectatorAnchor> spectatorAnchors(RunSnapshot snapshot) {
        List<SpectatorAnchor> anchors = new ArrayList<>();
        for (Player member : runs.onlineMembers()) {
            if (runs.playerState(member.getUniqueId()).map(state -> "ACTIVE".equals(state.lifeState)).orElse(false)) {
                anchors.add(new SpectatorAnchor(member.getLocation(), 48.0, member));
            }
        }
        long now = Instant.now().toEpochMilli();
        for (RunSnapshot.FacilityInstanceState facility : FacilityStateAccess.instances(snapshot).values()) {
            if (!"ACTIVE".equals(facility.state) || FacilityStateAccess.expired(facility, now)
                    || facility.world == null) continue;
            org.bukkit.World world = Bukkit.getWorld(facility.world);
            if (world != null) anchors.add(new SpectatorAnchor(
                    new Location(world, facility.x + 0.5, facility.y + 0.5, facility.z + 0.5), 32.0, null));
        }
        return anchors;
    }

    private boolean teleportRestricted(Player player, Location destination) {
        permittedRestrictedTeleports.add(player.getUniqueId());
        try {
            return player.teleport(destination);
        } finally {
            permittedRestrictedTeleports.remove(player.getUniqueId());
        }
    }

    private void trackLastSafeLocations(long now) {
        for (Player player : runs.onlineMembers()) {
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
            Location location = player.getLocation();
            if (state == null || !"ACTIVE".equals(state.lifeState) || !player.isOnGround()
                    || location.getBlock().isLiquid()
                    || !location.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) continue;
            runs.mutateTransient(run -> {
                RunSnapshot.PlayerState stored = run.players.get(player.getUniqueId().toString());
                stored.lastSafeWorld = location.getWorld().getName();
                stored.lastSafeX = location.getX();
                stored.lastSafeY = location.getY();
                stored.lastSafeZ = location.getZ();
                stored.lastSafeAtEpochMs = now;
            });
        }
    }

    private void rescueFromVoid(Player player, long now) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        Location destination = null;
        if (DeathRuntimePolicy.recentSafeLocation(now, state.lastSafeAtEpochMs) && state.lastSafeWorld != null) {
            org.bukkit.World world = Bukkit.getWorld(state.lastSafeWorld);
            if (world != null) destination = new Location(world, state.lastSafeX, state.lastSafeY, state.lastSafeZ);
        }
        if (destination == null) destination = player.getWorld().getSpawnLocation();
        runs.mutate(run -> run.players.get(player.getUniqueId().toString()).voidRescueUsed = true);
        player.teleport(destination);
        runs.broadcast(ChatColor.DARK_AQUA + player.getName() + "의 1회 공허 구조가 소모되었습니다.");
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CHORUS_FRUIT_TELEPORT, 0.8f, 0.7f);
    }

    private void requestHelp(Player player, RunSnapshot.PlayerState state) {
        long now = Instant.now().toEpochMilli();
        if (!DeathRuntimePolicy.helpSignalReady(now, state.helpSignalCooldownUntilEpochMs)) {
            double seconds = Math.ceil((state.helpSignalCooldownUntilEpochMs - now) / 1000.0);
            ActionBarService.critical(player, Component.text("도움 요청 재사용 " + (int) seconds + "초",
                    NamedTextColor.RED), 30);
            return;
        }
        runs.mutate(run -> run.players.get(player.getUniqueId().toString())
                .helpSignalCooldownUntilEpochMs = now + 10_000L);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0),
                28, 0.45, 0.8, 0.45, 0.02);
        for (Player member : runs.onlineMembers()) {
            if (member.equals(player)) continue;
            RunSnapshot.PlayerState memberState = runs.playerState(member.getUniqueId()).orElse(null);
            if (memberState == null || !"ACTIVE".equals(memberState.lifeState)) continue;
            String locationText = member.getWorld().equals(player.getWorld())
                    ? Math.round(member.getLocation().distance(player.getLocation())) + "m · "
                    + compassDirection(member.getLocation(), player.getLocation())
                    : "다른 차원";
            member.sendMessage(ChatColor.RED + "[도움 요청] " + player.getName() + " · " + locationText);
            ActionBarService.critical(member, Component.text("도움 요청: " + player.getName() + " · " + locationText,
                    NamedTextColor.RED), 60);
            member.playSound(member.getLocation(), Sound.BLOCK_BELL_USE, 0.75f, 1.2f);
        }
        ActionBarService.critical(player, Component.text("도움 요청을 보냈습니다.", NamedTextColor.YELLOW), 60);
        telemetry.event(runs.current().orElseThrow().runId, "DOWNED_HELP_SIGNAL",
                "{\"player\":\"" + player.getUniqueId() + "\"}");
    }

    private String compassDirection(Location from, Location to) {
        double degrees = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
        int index = Math.floorMod((int) Math.round(degrees / 45.0), 8);
        return List.of("북", "북동", "동", "남동", "남", "남서", "서", "북서").get(index);
    }

    private void applyDownedDamage(Player player, double finalDamage, double typeMultiplier) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        double damage = DeathRuntimePolicy.downedDamage(finalDamage, typeMultiplier, state.downedMaxHp);
        if (damage <= 0.0) return;
        double remaining = Math.max(0.0, state.downedHp - damage);
        runs.mutate(run -> {
            RunSnapshot.PlayerState current = run.players.get(player.getUniqueId().toString());
            current.downedHp = remaining;
            current.lastDamageAtEpochMs = Instant.now().toEpochMilli();
        });
        ActionBarService.critical(player, Component.text("빈사 체력 " + Math.round(remaining) + "/"
                + Math.round(state.downedMaxHp), remaining <= state.downedMaxHp * 0.25
                ? NamedTextColor.DARK_RED : NamedTextColor.RED), 45);
        if (remaining <= 0.0) completeDeath(player, "DOWNED_HP_ZERO");
    }

    private double downedDamageMultiplier(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return 0.50;
        LivingEntity source = combatAttacker(byEntity);
        if (source == null) return 0.50;
        String id = enemyId(source);
        ProductionContentCatalog.EnemyEntry entry = production.enemiesById().get(id);
        return id.startsWith("BOSS-") || production.bossesById().containsKey(id)
                || (entry != null && entry.elite()) ? 0.75 : 0.50;
    }

    private void completeDeath(Player player, String reason) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || "DEAD".equals(state.lifeState)) return;
        cancelAllReviveParticipation(player.getUniqueId(), "구조자 완전 사망");
        reviveSessions.remove(player.getUniqueId());
        if (!"DEAD_PENDING".equals(state.lifeState)) {
            runs.mutate(run -> run.players.get(player.getUniqueId().toString()).lifeState = "DEAD_PENDING");
        }
        if (!deathHandler.prepareDeath(player, reason)) {
            telemetry.event(runs.current().orElseThrow().runId, "PLAYER_DEATH_BLOCKED",
                    "{\"reason\":\"REMAINS_NOT_PERSISTED\"}");
            return;
        }
        statuses.clearOnDeath(player);
        restoreActiveMovementProfile(player);
        if (player.isOnline()) {
            player.setGameMode(GameMode.SPECTATOR);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.GLOWING);
        }
        runs.mutate(run -> run.players.get(player.getUniqueId().toString()).lifeState = "DEAD");
        runs.broadcast(ChatColor.DARK_RED + "[완전 사망] " + player.getName());
        for (Player member : runs.onlineMembers()) {
            ActionBarService.critical(member, Component.text("[완전 사망] " + player.getName(), NamedTextColor.DARK_RED), 80);
        }
        telemetry.event(runs.current().orElseThrow().runId, "PLAYER_STATE_CHANGED",
                "{\"state\":\"DEAD\",\"reason\":\"" + reason + "\"}");
    }

    private void defeatEnemy(Player attacker, LivingEntity target, String enemyId) {
        UUID entityId = target.getUniqueId();
        ProductionContentCatalog.EnemyEntry productionEnemy = production.enemiesById().get(enemyId);
        if (productionEnemy != null) {
            List<Player> contributors = productionContributors(entityId, attacker);
            if (productionEnemy.rewardsPlayers()) {
                productionLootHandler.reward("enemy:" + entityId, productionEnemy, contributors);
            }
            if (productionEnemy.activityExp() > 0) {
                for (Player contributor : contributors) {
                    growth.awardExp(contributor, productionEnemy.activityExp(),
                            "enemy-exp:" + entityId + ":" + contributor.getUniqueId());
                }
            }
            runs.commitOnce("enemy-defeat:" + entityId, "ENEMY_DEFEATED",
                    "{\"enemyId\":\"" + enemyId + "\",\"contributors\":" + contributors.size() + "}", run -> { });
        }
        target.remove();
        activeCombatEntities.remove(entityId);
        enemyContributions.remove(entityId);
        BossBar bar = breakBars.remove(entityId);
        if (bar != null) {
            bar.removeAll();
        }
        if (productionEnemy != null) return;
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
        RunSnapshot.PlayerState state = runs.playerState(attacker.getUniqueId()).orElse(null);
        double chance = weapon.statusChance() + (state == null ? 0.0 : PlayerStatPolicy.statusChanceBonus(state.investedStats))
                + growth.statusHitBonus(attacker, hasDamageOverTime(target));
        if (stage != weapon.attackCoefficients().size() - 1) return;
        applyStatus(attacker, target, weapon.statusId(), "basic:" + weapon.id(), chance,
                0.0, 0.0, executionId);
    }

    private StatusService.ApplyResult applyStatus(Player attacker, LivingEntity target, String statusId,
                                                   String sourceId, double chance, double durationSeconds,
                                                   double strength, String executionId) {
        StatusService.TargetGrade grade = statusTargetGrade(target);
        StatusService.ApplyResult result = statuses.apply(target, statusId,
                attacker == null ? null : attacker.getUniqueId(), sourceId, chance, durationSeconds,
                strength, executionId, grade);
        if (result.convertedToBreak()) {
            double maximum = target.getPersistentDataContainer().getOrDefault(breakMaxKey, PersistentDataType.DOUBLE, 0.0);
            double multiplier = attacker == null ? 1.0 : growth.breakMultiplier(attacker)
                    * (1.0 + Math.max(0.0, equipment.activeStat(attacker, "BREAK_DAMAGE")) / 100.0);
            applyBreak(target, maximum * result.breakFraction() * multiplier);
            if (attacker != null) {
                ActionBarService.notice(attacker, Component.text(statusId + " → BREAK 변환", NamedTextColor.YELLOW), 28);
            }
        }
        return result;
    }

    private StatusService.TargetGrade statusTargetGrade(LivingEntity target) {
        if (target instanceof Player) return StatusService.TargetGrade.PLAYER;
        String id = enemyId(target);
        if (id.startsWith("BOSS-") || production.bossesById().containsKey(id)) return StatusService.TargetGrade.BOSS;
        ProductionContentCatalog.EnemyEntry enemy = production.enemiesById().get(id);
        return enemy != null && enemy.elite() ? StatusService.TargetGrade.ELITE : StatusService.TargetGrade.NORMAL;
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
                && player.hasLineOfSight(living) ? java.util.Optional.of(living) : java.util.Optional.empty();
    }

    private void updateHud() {
        RunSnapshot snapshot = runs.current().orElse(null);
        if (snapshot == null || !"RUNNING".equals(snapshot.state)) {
            return;
        }
        for (Player player : runs.onlineMembers()) {
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
            if ("DOWNED".equals(state.lifeState) || "DOWNED_GRACE".equals(state.lifeState)
                    || "BEING_REVIVED".equals(state.lifeState)) {
                ActionBarService.renderHud(player, Component.text("빈사 체력 " + Math.round(state.downedHp) + "/"
                        + Math.round(state.downedMaxHp) + " | 부상 " + state.injuryStacks + "/3"
                        + ("DOWNED_GRACE".equals(state.lifeState) ? " | 보호"
                        : "BEING_REVIVED".equals(state.lifeState) ? " | 구조 " + Math.round(state.reviveProgress * 100.0) + "%"
                        : " | 웅크리기+우클릭 구조"),
                        NamedTextColor.RED));
                continue;
            }
            NamedTextColor color = state.ap <= 20.0 ? NamedTextColor.RED : NamedTextColor.AQUA;
            String status = statuses.summary(player);
            String weaponId = equipment.resolveWeaponId(player);
            String ammoText = usesArrowAmmo(weaponId)
                    ? " | 화살원장 " + AmmoLedgerPolicy.balance(state.ammoLedger, AmmoLedgerPolicy.GENERAL_ARROW)
                    + ("CROSSBOW".equals(weaponId) ? " (탄창 " + state.crossbowLoadedAmmo + ")" : "") : "";
            ActionBarService.renderHud(player, Component.text("Day " + snapshot.day + " | AP " + Math.round(state.ap) + "/" + state.maxAp
                    + " | Lv." + state.level + " | " + weaponId + ammoText
                    + (status.isBlank() ? "" : " | " + status), color));
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
            ProductionContentCatalog.BossEntry boss = production.bossesById().get(id);
            return boss == null ? 110.0 : boss.attackDamage();
        }
        ProductionContentCatalog.EnemyEntry enemy = production.enemiesById().get(id);
        if (enemy != null) return enemy.attackDamage();
        return contentEnemy(id).attackDamage();
    }

    private void processProductionEnemyActions() {
        long tick = runs.clockTick();
        Set<UUID> live = new HashSet<>();
        for (UUID uuid : new HashSet<>(activeCombatEntities)) {
            Entity found = findEntity(uuid.toString());
            if (!(found instanceof LivingEntity enemy) || !enemy.isValid() || enemy.isDead()) continue;
            ProductionContentCatalog.EnemyEntry definition = production.enemiesById().get(enemyId(enemy));
            if (definition == null) continue;
            live.add(uuid);
            if (statuses.blocksAllActions(enemy)) continue;
            ProductionContentCatalog.ActionBundleEntry bundle = production.actionBundlesById()
                    .get(definition.actionBundleId());
            if (bundle == null || bundle.actions().isEmpty()) continue;
            ProductionContentCatalog.ActionEntry action = bundle.actions().getFirst();
            EnemyActionState state = productionActionStates.computeIfAbsent(uuid,
                    ignored -> new EnemyActionState(tick + 20L));
            if (state.targetUuid != null) {
                Player target = Bukkit.getPlayer(state.targetUuid);
                if (!lockedProductionTargetValid(enemy, definition, target, action.range())) {
                    state.targetUuid = null;
                    state.executeAtTick = 0L;
                    state.nextReadyTick = tick;
                    continue;
                }
                if (tick < state.executeAtTick) {
                    if (tick % 5L == 0L) telegraphEnemyAction(enemy, target, action);
                    continue;
                }
                executeProductionEnemyAction(enemy, target, action);
                state.targetUuid = null;
                state.executeAtTick = 0L;
                state.nextReadyTick = tick + action.cooldownTicks();
                continue;
            }
            if (tick < state.nextReadyTick || !enemy.hasAI()
                    || enemy.getPersistentDataContainer().getOrDefault(groggyUntilKey,
                    PersistentDataType.LONG, 0L) > Instant.now().toEpochMilli()) continue;
            Player target = nearestProductionTarget(enemy, definition, action.range()).orElse(null);
            if (target == null) continue;
            state.targetUuid = target.getUniqueId();
            state.executeAtTick = tick + action.telegraphTicks();
            telegraphEnemyAction(enemy, target, action);
        }
        productionActionStates.keySet().removeIf(uuid -> !live.contains(uuid));
    }

    private java.util.Optional<Player> nearestProductionTarget(LivingEntity enemy,
                                                               ProductionContentCatalog.EnemyEntry definition,
                                                               double range) {
        double maximum = Math.max(3.0, range);
        UUID forcedTarget = statuses.tauntTarget(enemy).orElse(null);
        if (forcedTarget != null) {
            Player forced = Bukkit.getPlayer(forcedTarget);
            if (forced != null && forced.isOnline() && forced.getWorld().equals(enemy.getWorld())
                    && runs.playerState(forced.getUniqueId()).map(state -> "ACTIVE".equals(state.lifeState)).orElse(false)
                    && forced.getLocation().distanceSquared(enemy.getLocation()) <= maximum * maximum
                    && enemy.hasLineOfSight(forced)) return java.util.Optional.of(forced);
        }
        List<Player> candidates = runs.onlineMembers().stream()
                .filter(player -> player.getWorld().equals(enemy.getWorld()))
                .filter(player -> player.getLocation().distanceSquared(enemy.getLocation()) <= maximum * maximum)
                .filter(enemy::hasLineOfSight).toList();
        boolean activeAvailable = candidates.stream().anyMatch(player -> runs.playerState(player.getUniqueId())
                .map(state -> "ACTIVE".equals(state.lifeState)).orElse(false));
        boolean executor = definition.flags().contains("DOWNED_EXECUTOR");
        return candidates.stream().filter(player -> DeathRuntimePolicy.enemyTargetPriority(executor,
                        runs.playerState(player.getUniqueId()).map(state -> state.lifeState).orElse(""),
                        activeAvailable) < Integer.MAX_VALUE)
                .min(Comparator.comparingInt((Player player) -> DeathRuntimePolicy.enemyTargetPriority(executor,
                                runs.playerState(player.getUniqueId()).map(state -> state.lifeState).orElse(""),
                                activeAvailable))
                        .thenComparingDouble(player -> player.getLocation().distanceSquared(enemy.getLocation())));
    }

    private boolean lockedProductionTargetValid(LivingEntity enemy,
                                                ProductionContentCatalog.EnemyEntry definition,
                                                Player target, double range) {
        if (target == null || !target.isOnline() || !target.getWorld().equals(enemy.getWorld())
                || target.getLocation().distanceSquared(enemy.getLocation()) > Math.max(3.0, range) * Math.max(3.0, range)
                || !enemy.hasLineOfSight(target)) return false;
        String lifeState = runs.playerState(target.getUniqueId()).map(state -> state.lifeState).orElse("");
        if ("ACTIVE".equals(lifeState)) return true;
        if (!("DOWNED".equals(lifeState) || "BEING_REVIVED".equals(lifeState))) return false;
        if (definition.flags().contains("DOWNED_EXECUTOR")) return true;
        return runs.onlineMembers().stream().filter(player -> player.getWorld().equals(enemy.getWorld()))
                .filter(player -> runs.playerState(player.getUniqueId())
                        .map(state -> "ACTIVE".equals(state.lifeState)).orElse(false))
                .filter(player -> player.getLocation().distanceSquared(enemy.getLocation())
                        <= Math.max(3.0, range) * Math.max(3.0, range))
                .noneMatch(enemy::hasLineOfSight);
    }

    private void telegraphEnemyAction(LivingEntity enemy, Player target,
                                      ProductionContentCatalog.ActionEntry action) {
        if (target == null || !target.isOnline() || !target.getWorld().equals(enemy.getWorld())) return;
        enemy.getWorld().spawnParticle(Particle.ENCHANTED_HIT, enemy.getEyeLocation(), 8, 0.35, 0.35, 0.35, 0.01);
        enemy.getWorld().spawnParticle(Particle.DUST_PLUME, target.getLocation().add(0, 0.1, 0),
                5, 0.45, 0.02, 0.45, 0.0);
        if (runs.clockTick() % 10L == 0L) {
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.45f, 0.6f);
            ActionBarService.critical(target, Component.text(action.name() + " 전조", NamedTextColor.RED), 12);
        }
    }

    private void executeProductionEnemyAction(LivingEntity enemy, Player target,
                                              ProductionContentCatalog.ActionEntry action) {
        if (target == null || !target.isOnline() || !target.getWorld().equals(enemy.getWorld())
                || target.getLocation().distanceSquared(enemy.getLocation()) > action.range() * action.range()
                || !enemy.hasLineOfSight(target)) return;
        damagePlayerFromPattern(enemy, target, action.damage());
        if (!action.statusId().isBlank()
                && invulnerableUntilEpochMs.getOrDefault(target.getUniqueId(), 0L) < Instant.now().toEpochMilli()) {
            applyEnemyStatus(enemy, target, action);
        }
        enemy.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0),
                2, 0.2, 0.2, 0.2, 0.0);
        enemy.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.65f, 0.75f);
        telemetry.event(runs.current().orElseThrow().runId, "ENEMY_ACTION_COMMITTED",
                "{\"enemyId\":\"" + enemyId(enemy) + "\",\"actionId\":\"" + action.id()
                        + "\",\"target\":\"" + target.getUniqueId() + "\"}");
    }

    private void applyEnemyStatus(LivingEntity enemy, Player target, ProductionContentCatalog.ActionEntry action) {
        StatusService.ApplyResult result = statuses.apply(target, action.statusId(), enemy.getUniqueId(), action.id(),
                1.0, 0.0, 0.0, "enemy-status:" + enemy.getUniqueId() + ":" + action.id()
                        + ":" + runs.clockTick(), StatusService.TargetGrade.PLAYER);
        if (result.applied()) {
            ActionBarService.critical(target, Component.text(action.statusId() + " 상태이상", NamedTextColor.DARK_RED), 35);
        } else if ("RESISTED".equals(result.reason())) {
            ActionBarService.notice(target, Component.text(action.statusId() + " 저항", NamedTextColor.GRAY), 24);
        }
    }

    private boolean playerStatusActive(Player player, String id) {
        return statusActive(player, id);
    }

    private void showControlRestriction(Player player, String message) {
        ActionBarService.notice(player, Component.text(message, NamedTextColor.RED), 35);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.65f);
    }

    private List<Player> productionContributors(UUID entityId, Player fallback) {
        Set<UUID> ids = new HashSet<>(enemyContributions.getOrDefault(entityId, Map.of()).keySet());
        if (fallback != null) ids.add(fallback.getUniqueId());
        return ids.stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull)
                .filter(Player::isOnline).filter(runs::isMember)
                .sorted(Comparator.comparing(Player::getUniqueId)).toList();
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

    private boolean requireUsableWeapon(Player player) {
        if (equipment.isMainWeaponUsable(player)) return true;
        ActionBarService.notice(player,
                Component.text("장비가 파손되었습니다. 장비 메뉴에서 수리해야 합니다.", NamedTextColor.RED), 45);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.35f, 0.55f);
        return false;
    }

    private boolean isActionRestricted(Player player) {
        return runs.playerState(player.getUniqueId())
                .map(state -> !"ACTIVE".equals(state.lifeState)
                        || Instant.now().toEpochMilli() < state.reviveProtectionUntilEpochMs).orElse(true)
                || statuses.blocksAllActions(player) || isReviving(player);
    }

    private static boolean isDowned(String lifeState) {
        return "DOWNED_GRACE".equals(lifeState) || "DOWNED".equals(lifeState)
                || "BEING_REVIVED".equals(lifeState);
    }

    private void showRestrictedAction(Player player) {
        long now = Instant.now().toEpochMilli();
        String message = isReviving(player) ? "구조 중에는 전투·아이템·블록 행동을 할 수 없습니다."
                : runs.playerState(player.getUniqueId()).map(state -> now < state.reviveProtectionUntilEpochMs).orElse(false)
                ? "구조 직후 회복 보호 중에는 공격·아이템·시설 행동을 할 수 없습니다."
                : statuses.blocksAllActions(player)
                ? "강한 제어 상태에서는 행동할 수 없습니다."
                : "빈사·사망 상태에서는 이동과 도움 요청 외 행동을 할 수 없습니다.";
        ActionBarService.notice(player, Component.text(message, NamedTextColor.RED), 30);
    }

    private void applySkillEffect(Player attacker, LivingEntity target, PrototypeContent.SkillDefinition skill,
                                  String executionId) {
        switch (skill.effect()) {
            case "SLOW" -> applyStatus(attacker, target, "SLOW", skill.id(), 1.0,
                    3.0, 0.30, executionId + ":slow");
            case "ROOT" -> applyStatus(attacker, target, "ROOT", skill.id(), 1.0,
                    0.8, 1.0, executionId + ":root");
            case "MARK" -> applyStatus(attacker, target, "MARK", skill.id(), 1.0,
                    markDuration(skill.id()), 0.10, executionId + ":mark");
            case "BLEED" -> applyDamageOverTime(attacker, target, skill, "bleed", 4, 20L, 7.0);
            case "POISON" -> {
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0, true, true));
                applyDamageOverTime(attacker, target, skill, "poison", 4, 25L, 5.0);
            }
            case "BURN" -> {
                target.setFireTicks(Math.max(target.getFireTicks(), 80));
                applyDamageOverTime(attacker, target, skill, "burn", 4, 20L, 8.0);
            }
            case "VULNERABLE" -> applyStatus(attacker, target, "VULNERABLE", skill.id(), 1.0,
                    6.0, 0.20, executionId + ":vulnerable");
            case "ARMOR_SHRED" -> applyStatus(attacker, target, "ARMOR_BREAK", skill.id(), 1.0,
                    5.0, 20.0, executionId + ":armor-break");
            default -> { }
        }
    }

    private static double markDuration(String skillId) {
        if (skillId.contains("focused_duel")) return 8.0;
        if (skillId.contains("rally_lunge")) return 5.0;
        return 6.0;
    }

    private void applyCasterSkillEffect(Player player, PrototypeContent.SkillDefinition skill, List<LivingEntity> targets) {
        String id = skill.id();
        if (id.contains("rally_lunge")) {
            player.setVelocity(player.getLocation().getDirection().normalize().multiply(1.15).setY(0.12));
        } else if (id.contains("shadowstep")) {
            player.setVelocity(player.getLocation().getDirection().normalize().multiply(0.9).setY(0.08));
        } else if (id.contains("fading_feint")) {
            player.setVelocity(player.getLocation().getDirection().normalize().multiply(-0.9).setY(0.08));
        }
        if (skill.effect().equals("HEAL") || id.contains("purifying_field")) {
            for (Player member : runs.onlineMembers()) if (sameWorldWithin(player, member, 5.0)) {
                heal(member, 3.0);
                cleanseWeakEffects(member);
            }
        }
        if (id.contains("rescue_flare")) {
            for (Player member : runs.onlineMembers()) if (sameWorldWithin(player, member, 4.0)) {
                member.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 0, true, true));
            }
        }
        if (id.contains("turning_guard") || id.contains("bulwark_strike") || id.contains("centered_stance")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    id.contains("centered_stance") ? 100 : 40, 0, true, true));
        }
        if (id.contains("guard_intercept")) {
            for (Player member : runs.onlineMembers()) if (sameWorldWithin(player, member, 3.0)) {
                member.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, true, true));
            }
        }
    }

    private void executeCommonEffect(Player player, PrototypeContent.SkillDefinition skill, LivingEntity target) {
        String id = skill.id();
        switch (skill.effect()) {
            case "HEAL" -> {
                if (id.contains("shared_breath")) {
                    for (Player member : runs.onlineMembers()) if (!member.equals(player) && sameWorldWithin(player, member, 5.0)) {
                        runs.mutate(run -> {
                            RunSnapshot.PlayerState state = run.players.get(member.getUniqueId().toString());
                            state.ap = Math.min(state.maxAp, state.ap + 8.0);
                        });
                    }
                } else {
                    double maximum = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0
                            : player.getAttribute(Attribute.MAX_HEALTH).getValue();
                    heal(player, maximum * 0.08 + 4.0);
                    player.removePotionEffect(PotionEffectType.POISON);
                }
            }
            case "CLEANSE" -> cleanseWeakEffects(player);
            case "AP_STIM" -> runs.mutate(run -> {
                RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                state.ap = Math.min(state.maxAp, state.ap + 25.0);
            });
            case "MARK" -> {
                if (target != null) applyStatus(player, target, "MARK", skill.id(), 1.0,
                        6.0, 0.10, "common-mark:" + skill.id() + ":" + UUID.randomUUID());
            }
            case "RESCUE_PULL" -> pullDowned(player, (Player) target);
            case "COVER" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 240, 1, true, true));
                coverFields.put(player.getUniqueId(), new CoverField(player.getLocation().clone(),
                        Instant.now().plusSeconds(12).toEpochMilli()));
                player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation().add(0, 1, 0),
                        60, 1.5, 1.0, 0.4, Material.COBBLESTONE.createBlockData());
            }
            case "SUPPORT" -> {
                if (id.contains("guard_step")) {
                    player.setVelocity(player.getLocation().getDirection().normalize().multiply(0.75).setY(0.08));
                    invulnerableUntilEpochMs.put(player.getUniqueId(), Instant.now().plusMillis(800).toEpochMilli());
                } else if (id.contains("break_call") && target != null) {
                    setTimedStatus(target, "break_call", 80L);
                }
            }
            default -> { }
        }
    }

    private boolean requireSkillReady(Player player, PrototypeContent.SkillDefinition skill) {
        long remaining = skillReadyAtNanos.getOrDefault(player.getUniqueId() + ":" + skill.id(), 0L) - System.nanoTime();
        if (remaining <= 0L) return true;
        ActionBarService.notice(player, Component.text(skill.name() + " 재사용 대기 "
                + String.format(java.util.Locale.ROOT, "%.1f", remaining / 1_000_000_000.0) + "초", NamedTextColor.RED), 25);
        return false;
    }

    private void startSkillCooldown(Player player, PrototypeContent.SkillDefinition skill) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        double multiplier = runs.isTestRun() && state != null ? clamp(state.testCooldownMultiplier, 0.05, 10.0) : 1.0;
        long ticks = CombatMath.cooldownTicks(skills.cooldownTicks(skill.id()), multiplier);
        skillReadyAtNanos.put(player.getUniqueId() + ":" + skill.id(), System.nanoTime() + ticks * 50_000_000L);
    }

    private void setTimedStatus(LivingEntity target, String id, long durationTicks) {
        StatusService.ApplyResult result = statuses.applyGuaranteed(target, id, null, "INTERNAL",
                durationTicks / 20.0, 0.0, "internal:" + id + ":" + UUID.randomUUID(), statusTargetGrade(target));
        if (!"UNKNOWN_STATUS".equals(result.reason())) return;
        target.getPersistentDataContainer().set(new NamespacedKey(plugin, "status_" + id), PersistentDataType.LONG,
                Instant.now().plusMillis(durationTicks * 50L).toEpochMilli());
    }

    private boolean statusActive(LivingEntity target, String id) {
        if (statuses.active(target, id)) return true;
        NamespacedKey key = new NamespacedKey(plugin, "status_" + id);
        long expiry = target.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L);
        if (expiry > Instant.now().toEpochMilli()) return true;
        target.getPersistentDataContainer().remove(key);
        return false;
    }

    private boolean hasDamageOverTime(LivingEntity target) {
        return statusActive(target, "bleed") || statusActive(target, "poison") || statusActive(target, "burn");
    }

    private void applyDamageOverTime(Player attacker, LivingEntity target, PrototypeContent.SkillDefinition skill,
                                     String status, int pulses, long intervalTicks, double rawDamage) {
        StatusService.ApplyResult applied = applyStatus(attacker, target, status, skill.id(), 1.0,
                (pulses * intervalTicks + 10L) / 20.0, 1.0,
                "dot-apply:" + skill.id() + ":" + UUID.randomUUID());
        if (!applied.applied()) return;
        for (int pulse = 1; pulse <= pulses; pulse++) {
            int sequence = pulse;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (target.isValid() && !target.isDead() && isCombatEntity(target)) {
                    damageCombatEntity(attacker, target, rawDamage, 0.0,
                            "dot:" + skill.id() + ":" + target.getUniqueId() + ":" + sequence);
                }
            }, pulse * intervalTicks);
        }
    }

    private void heal(Player player, double amount) {
        double maximum = player.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0
                : player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double reduction = Math.min(0.90, Math.max(0.0, statuses.strength(player, "HEAL_REDUCTION")));
        player.setHealth(Math.min(maximum, player.getHealth() + amount * (1.0 - reduction)));
    }

    private void cleanseWeakEffects(Player player) {
        statuses.cleanseDispellable(player, 64);
        player.removePotionEffect(PotionEffectType.POISON);
        player.removePotionEffect(PotionEffectType.WITHER);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    private boolean sameWorldWithin(Player source, Player target, double range) {
        return source.getWorld().equals(target.getWorld())
                && source.getLocation().distanceSquared(target.getLocation()) <= range * range;
    }

    private boolean protectedByCover(Player player) {
        long now = Instant.now().toEpochMilli();
        coverFields.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMs < now);
        return coverFields.values().stream().anyMatch(field -> field.location.getWorld() != null
                && field.location.getWorld().equals(player.getWorld())
                && field.location.distanceSquared(player.getLocation()) <= 2.5 * 2.5);
    }

    private java.util.Optional<Player> nearestDowned(Player player) {
        return runs.onlineMembers().stream().filter(member -> !member.equals(player) && sameWorldWithin(player, member, 8.0))
                .filter(member -> runs.playerState(member.getUniqueId()).map(state -> "DOWNED".equals(state.lifeState)).orElse(false))
                .min(Comparator.comparingDouble(member -> member.getLocation().distanceSquared(player.getLocation())));
    }

    private void pullDowned(Player player, Player member) {
        Vector away = player.getLocation().toVector().subtract(member.getLocation().toVector());
        if (away.lengthSquared() < 0.01) away = player.getLocation().getDirection().multiply(-1.0);
        Location destination = player.getLocation().clone().subtract(away.normalize().multiply(1.5));
        teleportRestricted(member, destination);
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

    private boolean hasMaterial(Player player, Material material) {
        return hasMaterial(player, material, 1);
    }

    private boolean hasMaterial(Player player, Material material, int amount) {
        int found = 0;
        for (int slot = 1; slot <= 35; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && item.getType() == material) found += item.getAmount();
            if (found >= amount) return true;
        }
        return false;
    }

    private static boolean usesArrowAmmo(String weaponId) {
        return "BOW".equals(weaponId) || "CROSSBOW".equals(weaponId);
    }

    private boolean takeOneMaterial(Player player, Material material) {
        return takeMaterial(player, material, 1);
    }

    private boolean takeMaterial(Player player, Material material, int amount) {
        if (!hasMaterial(player, material, amount)) return false;
        int remaining = amount;
        for (int slot = 1; slot <= 35; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType() != material || item.getAmount() <= 0) continue;
            int consumed = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - consumed);
            if (item.getAmount() <= 0) player.getInventory().setItem(slot, null);
            remaining -= consumed;
            if (remaining == 0) return true;
        }
        return true;
    }

    private boolean hasArrowAmmo(Player player, String weaponId) {
        if ("CROSSBOW".equals(weaponId)) {
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
            if (state != null && state.crossbowLoadedAmmo > 0) return true;
        }
        if (ammoService != null && ammoService.generalArrowBalance(player) > 0) return true;
        return hasMaterial(player, Material.ARROW);
    }

    private boolean consumeArrowAmmo(Player player, String weaponId) {
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < growth.ammoConserveChance(player)) return true;
        if ("CROSSBOW".equals(weaponId)) {
            RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
            if (state != null && state.crossbowLoadedAmmo > 0) {
                runs.mutate(run -> run.players.get(player.getUniqueId().toString()).crossbowLoadedAmmo--);
                return true;
            }
        }
        if (ammoService != null && ammoService.consumeGeneralArrows(player, 1)) return true;
        return takeOneMaterial(player, Material.ARROW);
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

    private record CoverField(Location location, long expiresAtEpochMs) { }

    private record SpectatorAnchor(Location location, double radius, Player player) {
        private boolean contains(Location other) {
            return location.getWorld() != null && location.getWorld().equals(other.getWorld())
                    && DeathRuntimePolicy.insideBoundary(location.distanceSquared(other), radius);
        }

        private double distanceSquared(Location other) {
            return location.getWorld() != null && location.getWorld().equals(other.getWorld())
                    ? location.distanceSquared(other) : Double.MAX_VALUE;
        }
    }

    private static final class EnemyActionState {
        private long nextReadyTick;
        private UUID targetUuid;
        private long executeAtTick;

        private EnemyActionState(long nextReadyTick) {
            this.nextReadyTick = nextReadyTick;
        }
    }

    private record EnemyHitPermit(UUID enemyUuid, UUID playerUuid) { }

    private record QuickUseChannel(String itemId, int slot, long startedAtTick, long completesAtTick) { }

    public record DamagePreview(double rawDamage, double defenceFactor, double augmentDamageMultiplier,
                                double testDamageMultiplier, double finalDamage, double rawBreak,
                                double augmentBreakMultiplier, double testBreakMultiplier, double finalBreak) {
    }

    private static final class ComboState {
        private String weaponId;
        private int stage;
        private long lastAttackAtEpochMs;
    }

    private static final class ReviveSession {
        private final UUID target;
        private final Map<UUID, ReviveContributor> contributors = new HashMap<>();
        private long lastTickAtEpochMs;

        private ReviveSession(UUID target, long lastTickAtEpochMs) {
            this.target = target;
            this.lastTickAtEpochMs = lastTickAtEpochMs;
        }
    }

    private static final class ReviveContributor {
        private final UUID playerUuid;
        private final long startedAtEpochMs;
        private final Location anchor;

        private ReviveContributor(UUID playerUuid, long startedAtEpochMs, Location anchor) {
            this.playerUuid = playerUuid;
            this.startedAtEpochMs = startedAtEpochMs;
            this.anchor = anchor;
        }
    }

    public interface BossDamageHandler {
        void damage(Player attacker, LivingEntity boss, double damage, double breakDamage, String executionId);
    }

    @FunctionalInterface
    public interface DeathHandler {
        boolean prepareDeath(Player player, String reason);
    }

    @FunctionalInterface
    public interface ItemRewardHandler {
        void reward(Player player, String resourceId, int amount);
    }

    @FunctionalInterface
    public interface ProductionLootHandler {
        void reward(String transactionId, ProductionContentCatalog.EnemyEntry enemy, List<Player> contributors);
    }
}
