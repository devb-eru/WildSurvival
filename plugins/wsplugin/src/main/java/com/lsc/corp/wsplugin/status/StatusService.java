package com.lsc.corp.wsplugin.status;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.EquipmentService;
import com.lsc.corp.wsplugin.run.RunService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Server-authoritative status instances. Vanilla effects are presentation helpers only. */
public final class StatusService implements Listener {
    private static final long CONTROL_RESET_MILLIS = 6_000L;
    private static final long OVERCONTROL_WATCH_MILLIS = 9_000L;
    private static final long ALL_STATUS_IMMUNITY_MILLIS = 6_000L;
    private static final long CONTROL_BREAK_IMMUNITY_MILLIS = 500L;
    private static final int STANDARD_INSTANCE_CAP = 64;
    private static final int STANDARD_DOT_CAP = 32;

    private final RunService runs;
    private final ProductionContentCatalog production;
    private final EquipmentService equipment;
    private final TelemetryService telemetry;
    private final NamespacedKey stateKey;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final Map<UUID, TargetState> states = new HashMap<>();
    private final Set<UUID> tracked = new HashSet<>();

    public StatusService(JavaPlugin plugin, RunService runs, ProductionContentCatalog production,
                         EquipmentService equipment, TelemetryService telemetry) {
        this.runs = runs;
        this.production = production;
        this.equipment = equipment;
        this.telemetry = telemetry;
        this.stateKey = new NamespacedKey(plugin, "status_runtime_v1");
    }

    public ApplyResult apply(LivingEntity target, String rawStatusId, UUID sourceEntityId, String sourceId,
                             double baseChance, double durationOverrideSeconds, double strengthOverride,
                             String executionId, TargetGrade targetGrade) {
        return applyInternal(target, rawStatusId, sourceEntityId, sourceId, baseChance,
                durationOverrideSeconds, strengthOverride, executionId, targetGrade, false);
    }

    public ApplyResult applyTestOverride(LivingEntity target, String rawStatusId, double durationSeconds,
                                         double strength, String executionId) {
        return applyInternal(target, rawStatusId, null, "TEST_LAB", 1.0, durationSeconds,
                strength, executionId, target instanceof Player ? TargetGrade.PLAYER : TargetGrade.NORMAL, true);
    }

    private ApplyResult applyInternal(LivingEntity target, String rawStatusId, UUID sourceEntityId, String sourceId,
                                      double baseChance, double durationOverrideSeconds, double strengthOverride,
                                      String executionId, TargetGrade targetGrade, boolean ignoreResistance) {
        ProductionContentCatalog.StatusEntry definition = definition(rawStatusId);
        if (definition == null) return ApplyResult.rejected("UNKNOWN_STATUS");
        if (!definition.executableBaseline() || definition.maxStacks() <= 0) {
            return ApplyResult.rejected("TEMPLATE_LOCKED");
        }
        if (target == null || !target.isValid() || target.isDead()) return ApplyResult.rejected("INVALID_TARGET");
        long now = runs.clockNowMillis();
        TargetState state = state(target);
        expire(target, state, now);
        if (state.allStatusImmunityUntilEpochMs > now) return ApplyResult.rejected("ALL_STATUS_IMMUNE");
        if (target instanceof Player player) {
            long protectedUntil = runs.playerState(player.getUniqueId())
                    .map(value -> value.reviveProtectionUntilEpochMs).orElse(0L);
            if (protectedUntil > now && (definition.tags().contains("HARD_CC")
                    || definition.tags().contains("ACTION_LOCK") || definition.tags().contains("DOT"))) {
                return ApplyResult.rejected("REVIVE_PROTECTION");
            }
        }

        if ("TAUNT".equals(definition.id()) && sourceEntityId != null) {
            Entity source = Bukkit.getEntity(sourceEntityId);
            if (target instanceof Player targetPlayer && source instanceof Player sourcePlayer
                    && runs.isMember(targetPlayer) && runs.isMember(sourcePlayer)) {
                return ApplyResult.rejected("SAME_TEAM");
            }
        }

        boolean hardControl = isHardControl(definition);
        boolean actionLock = definition.tags().contains("ACTION_LOCK");
        if (hardControl && state.controlImmunityUntilEpochMs > now) {
            return ApplyResult.rejected("CONTROL_BREAK_IMMUNE");
        }

        if (!ignoreResistance) {
            double chance = StatusRuntimePolicy.applicationChance(definition.resistPolicy(), baseChance,
                    sourceHitBonus(sourceEntityId), targetResistance(target));
            double roll = deterministicRoll(executionId, target.getUniqueId(), definition.id());
            if (roll >= chance) return ApplyResult.rejected("RESISTED");
        }

        double baseDuration = durationOverrideSeconds > 0.0
                ? durationOverrideSeconds : definition.standardDurationSeconds();
        double maximum = definition.standardMaxPreResistSeconds();
        double gradeMultiplier = targetGrade == TargetGrade.ELITE ? 0.50 : 1.0;
        double strength = strengthOverride > 0.0 ? strengthOverride : definition.baseStrength();
        if (targetGrade == TargetGrade.BOSS) {
            switch (definition.bossPolicy()) {
                case "IMMUNE" -> { return ApplyResult.rejected("BOSS_IMMUNE"); }
                case "BREAK_CONVERT" -> {
                    double preResist = maximum <= 0.0 ? baseDuration : Math.min(baseDuration, maximum);
                    return ApplyResult.converted(StatusRuntimePolicy.bossBreakFraction(preResist, 1.0, false));
                }
                case "REDUCED" -> {
                    gradeMultiplier *= 0.50;
                    strength *= 0.50;
                }
                default -> { }
            }
        }

        double chainMultiplier = 1.0;
        if (hardControl) {
            chainMultiplier = prepareHardControl(state, now);
            if (chainMultiplier <= 0.0) return ApplyResult.rejected("CONTROL_CHAIN_IMMUNE");
        } else if (actionLock) {
            chainMultiplier = prepareActionLock(state, now);
            if (chainMultiplier <= 0.0) return ApplyResult.rejected("ACTION_LOCK_IMMUNE");
        }

        long durationMillis = StatusRuntimePolicy.durationMillis(baseDuration, maximum, gradeMultiplier,
                targetTenacity(target), !"NONE".equals(definition.tenacityPolicy()), chainMultiplier, hardControl);
        if (durationMillis <= 0L) return ApplyResult.rejected("ZERO_DURATION");
        if (!hasCapacity(state, definition)) return ApplyResult.rejected("INSTANCE_CAP");

        StackResult stack = stack(state, definition, sourceEntityId, sourceId, now, durationMillis, strength);
        if (!stack.changed) return ApplyResult.rejected("STACK_REJECTED");
        if (hardControl && state.hardChainStartedAtEpochMs == 0L) state.hardChainStartedAtEpochMs = now;
        tracked.add(target.getUniqueId());
        save(target, state);
        applyVisual(target, definition, durationMillis, stack.stacks);
        telemetry("STATUS_APPLIED", definition.id(), target, sourceId,
                "\"stacks\":" + stack.stacks + ",\"durationMs\":" + durationMillis);
        return new ApplyResult(true, false, "APPLIED", 0.0, durationMillis, stack.stacks);
    }

    public ApplyResult applyGuaranteed(LivingEntity target, String statusId, UUID sourceEntityId,
                                       String sourceId, double durationSeconds, double strength,
                                       String executionId, TargetGrade grade) {
        ProductionContentCatalog.StatusEntry entry = definition(statusId);
        if (entry == null) return ApplyResult.rejected("UNKNOWN_STATUS");
        String policy = entry.resistPolicy();
        double chance = "RESISTIBLE".equals(policy) ? 0.95 : 1.0;
        return apply(target, statusId, sourceEntityId, sourceId, chance, durationSeconds, strength,
                executionId, grade);
    }

    public boolean active(LivingEntity target, String rawStatusId) {
        ProductionContentCatalog.StatusEntry definition = definition(rawStatusId);
        if (definition == null || target == null) return false;
        TargetState state = state(target);
        boolean changed = expire(target, state, runs.clockNowMillis());
        if (changed) save(target, state);
        return !state.active.getOrDefault(definition.id(), List.of()).isEmpty();
    }

    public int stacks(LivingEntity target, String rawStatusId) {
        ProductionContentCatalog.StatusEntry definition = definition(rawStatusId);
        if (definition == null || target == null) return 0;
        TargetState state = state(target);
        expire(target, state, runs.clockNowMillis());
        return state.active.getOrDefault(definition.id(), List.of()).stream().mapToInt(value -> value.stacks).sum();
    }

    public double strength(LivingEntity target, String rawStatusId) {
        ProductionContentCatalog.StatusEntry definition = definition(rawStatusId);
        if (definition == null || target == null) return 0.0;
        TargetState state = state(target);
        expire(target, state, runs.clockNowMillis());
        List<InstanceState> instances = state.active.getOrDefault(definition.id(), List.of());
        if ("STACK_INTENSITY".equals(definition.stacking())) {
            return instances.stream().mapToDouble(value -> value.strength * value.stacks).sum();
        }
        return instances.stream().mapToDouble(value -> value.strength).max().orElse(0.0);
    }

    public boolean blocksAllActions(LivingEntity target) {
        return active(target, "STUN") || active(target, "AIRBORNE") || active(target, "SLEEP")
                || active(target, "FEAR") || active(target, "FREEZE");
    }

    public boolean blocksMovement(LivingEntity target) {
        return blocksAllActions(target) || active(target, "ROOT");
    }

    public boolean blocksDodge(Player target) {
        return blocksMovement(target);
    }

    public boolean blocksWeaponAttack(Player target) {
        return blocksAllActions(target) || active(target, "DISARM");
    }

    public boolean blocksWeaponSkill(Player target) {
        return blocksAllActions(target) || active(target, "DISARM") || active(target, "SILENCE");
    }

    public boolean blocksCommonSkill(Player target) {
        return blocksAllActions(target) || active(target, "SILENCE");
    }

    /** Returns the server-authoritative hostile target locked by TAUNT, if the source still has a UUID. */
    public Optional<UUID> tauntTarget(LivingEntity target) {
        if (target == null) return Optional.empty();
        TargetState state = state(target);
        boolean changed = expire(target, state, runs.clockNowMillis());
        if (changed) save(target, state);
        return state.active.getOrDefault("TAUNT", List.of()).stream()
                .sorted(Comparator.comparingDouble((InstanceState value) -> value.strength).reversed()
                        .thenComparingLong(value -> value.appliedAtEpochMs))
                .map(value -> value.sourceEntityId)
                .filter(value -> value != null && !value.isBlank() && !"SYSTEM".equals(value))
                .map(value -> {
                    try { return UUID.fromString(value); }
                    catch (IllegalArgumentException ignored) { return null; }
                })
                .filter(Objects::nonNull)
                .findFirst();
    }

    public int remove(LivingEntity target, String rawStatusId, int maximumStacks) {
        ProductionContentCatalog.StatusEntry definition = definition(rawStatusId);
        if (definition == null || target == null || maximumStacks <= 0) return 0;
        TargetState state = state(target);
        List<InstanceState> instances = state.active.get(definition.id());
        if (instances == null || instances.isEmpty()) return 0;
        int remaining = maximumStacks;
        int removed = 0;
        while (remaining > 0 && !instances.isEmpty()) {
            InstanceState instance = instances.getLast();
            int take = Math.min(remaining, Math.max(1, instance.stacks));
            instance.stacks -= take;
            remaining -= take;
            removed += take;
            if (instance.stacks <= 0) instances.removeLast();
        }
        if (instances.isEmpty()) {
            state.active.remove(definition.id());
            clearVisual(target, definition);
        }
        save(target, state);
        if (removed > 0) telemetry("STATUS_REMOVED", definition.id(), target, "ITEM", "\"stacks\":" + removed);
        return removed;
    }

    public int cleanseDispellable(Player target, int maximum) {
        return cleanse(target, Set.of("DISPELLABLE"), maximum, false);
    }

    public int cleanseControl(Player target, int maximum) {
        return cleanse(target, Set.of("CONTROL_BREAK"), maximum, true);
    }

    public void clearOnDeath(LivingEntity target) {
        TargetState state = state(target);
        for (String id : new ArrayList<>(state.active.keySet())) {
            ProductionContentCatalog.StatusEntry definition = definition(id);
            if (definition != null && !"CORRUPTION".equals(definition.id())) {
                state.active.remove(id);
                clearVisual(target, definition);
            }
        }
        resetControlState(state);
        save(target, state);
    }

    public void clearControls(LivingEntity target) {
        clearTransientControls(target);
    }

    public void clearAll(LivingEntity target) {
        TargetState state = state(target);
        for (String id : new ArrayList<>(state.active.keySet())) {
            ProductionContentCatalog.StatusEntry definition = definition(id);
            if (definition != null) clearVisual(target, definition);
        }
        state.active.clear();
        resetControlState(state);
        save(target, state);
    }

    public String summary(Player player) {
        TargetState state = state(player);
        expire(player, state, runs.clockNowMillis());
        return state.active.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(value -> new Summary(entry.getKey(), value)))
                .sorted(Comparator.comparingInt(value -> {
                    ProductionContentCatalog.StatusEntry definition = definition(value.id);
                    return definition == null ? 99 : definition.visualPriority();
                }))
                .findFirst().map(value -> value.id + (value.instance.stacks > 1 ? "×" + value.instance.stacks : ""))
                .orElse("");
    }

    public void tick() {
        long now = runs.clockNowMillis();
        for (UUID uuid : new HashSet<>(tracked)) {
            Entity found = Bukkit.getEntity(uuid);
            TargetState state = states.get(uuid);
            if (state == null) {
                tracked.remove(uuid);
                continue;
            }
            if (!(found instanceof LivingEntity target)) {
                if (latestExpiry(state) <= now) {
                    states.remove(uuid);
                    tracked.remove(uuid);
                }
                continue;
            }
            boolean changed = expire(target, state, now);
            boolean hardActive = containsHardControl(state);
            if (hardActive && state.hardChainStartedAtEpochMs > 0L
                    && now - state.hardChainStartedAtEpochMs >= OVERCONTROL_WATCH_MILLIS) {
                removeHardControls(target, state);
                state.allStatusImmunityUntilEpochMs = now + ALL_STATUS_IMMUNITY_MILLIS;
                state.lastHardControlEndedAtEpochMs = now;
                changed = true;
                telemetry("STATUS_OVERCONTROL_IMMUNITY", "ALL", target, "SYSTEM", "\"durationMs\":6000");
            } else if (!hardActive && state.lastHardControlEndedAtEpochMs > 0L
                    && now - state.lastHardControlEndedAtEpochMs >= CONTROL_RESET_MILLIS) {
                state.hardChainStartedAtEpochMs = 0L;
                state.hardChainStage = 0;
                state.lastHardControlEndedAtEpochMs = 0L;
                changed = true;
            }
            if (changed) save(target, state);
        }
    }

    public void shutdown() {
        for (Player player : runs.onlineMembers()) clearTransientControls(player);
        states.clear();
        tracked.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!runs.isRunningMember(event.getPlayer()) || event.getTo() == null || !blocksMovement(event.getPlayer())) return;
        if (event.getFrom().getX() == event.getTo().getX() && event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getZ() == event.getTo().getZ()) return;
        event.setTo(event.getFrom().clone().setDirection(event.getTo().getDirection()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (runs.isMember(event.getPlayer())) clearTransientControls(event.getPlayer());
    }

    private int cleanse(Player target, Set<String> categories, int maximum, boolean controlBreak) {
        if (maximum <= 0) return 0;
        TargetState state = state(target);
        List<String> eligible = state.active.keySet().stream().filter(id -> {
            ProductionContentCatalog.StatusEntry definition = definition(id);
            return definition != null && categories.contains(definition.cleanseCategory());
        }).sorted(Comparator.comparingInt(id -> {
            ProductionContentCatalog.StatusEntry definition = definition(id);
            return StatusRuntimePolicy.cleansePriority(definition.visualPriority(), isHardControl(definition),
                    definition.tags().contains("DOT"));
        })).toList();
        int removed = 0;
        for (String id : eligible) {
            if (removed >= maximum) break;
            ProductionContentCatalog.StatusEntry definition = definition(id);
            state.active.remove(id);
            clearVisual(target, definition);
            removed++;
        }
        if (removed > 0 && controlBreak) state.controlImmunityUntilEpochMs = runs.clockNowMillis() + CONTROL_BREAK_IMMUNITY_MILLIS;
        save(target, state);
        return removed;
    }

    private StackResult stack(TargetState state, ProductionContentCatalog.StatusEntry definition,
                              UUID sourceEntityId, String sourceId, long now, long durationMillis, double strength) {
        List<InstanceState> instances = state.active.computeIfAbsent(definition.id(), ignored -> new ArrayList<>());
        long expires = now + durationMillis;
        String sourceEntity = sourceEntityId == null ? "SYSTEM" : sourceEntityId.toString();
        String source = sourceId == null || sourceId.isBlank() ? "UNKNOWN" : sourceId;
        switch (definition.stacking()) {
            case "REFRESH" -> {
                if (instances.isEmpty()) instances.add(instance(definition.id(), sourceEntity, source, now, expires, strength));
                else {
                    InstanceState current = instances.getFirst();
                    current.expiresAtEpochMs = Math.max(current.expiresAtEpochMs, expires);
                    current.strength = Math.max(current.strength, strength);
                }
            }
            case "REPLACE_STRONGER" -> {
                if (instances.isEmpty()) instances.add(instance(definition.id(), sourceEntity, source, now, expires, strength));
                else {
                    InstanceState current = instances.getFirst();
                    if (strength + 1.0e-9 < current.strength) return new StackResult(false, current.stacks);
                    current.strength = strength;
                    current.expiresAtEpochMs = Math.max(current.expiresAtEpochMs, expires);
                    current.sourceEntityId = sourceEntity;
                    current.sourceId = source;
                }
            }
            case "STACK_INTENSITY" -> {
                if (instances.isEmpty()) instances.add(instance(definition.id(), sourceEntity, source, now, expires, strength));
                else {
                    InstanceState current = instances.getFirst();
                    current.stacks = Math.min(definition.maxStacks(), current.stacks + 1);
                    current.expiresAtEpochMs = expires;
                    current.strength = Math.max(current.strength, strength);
                }
            }
            case "UNIQUE_SOURCE" -> {
                InstanceState same = instances.stream().filter(value -> value.sourceEntityId.equals(sourceEntity)
                        && value.sourceId.equals(source)).findFirst().orElse(null);
                if (same != null) {
                    same.expiresAtEpochMs = expires;
                    same.strength = Math.max(same.strength, strength);
                } else if (instances.size() < definition.maxStacks()) {
                    instances.add(instance(definition.id(), sourceEntity, source, now, expires, strength));
                } else return new StackResult(false, instances.size());
            }
            case "STACK_INDEPENDENT" -> {
                if (instances.size() >= definition.maxStacks()) return new StackResult(false, instances.size());
                instances.add(instance(definition.id(), sourceEntity, source, now, expires, strength));
            }
            case "NO_REFRESH" -> {
                if (!instances.isEmpty()) return new StackResult(false, instances.size());
                instances.add(instance(definition.id(), sourceEntity, source, now, expires, strength));
            }
            default -> { return new StackResult(false, instances.size()); }
        }
        return new StackResult(true, instances.stream().mapToInt(value -> value.stacks).sum());
    }

    private boolean expire(LivingEntity target, TargetState state, long now) {
        boolean changed = false;
        boolean hardBefore = containsHardControl(state);
        for (String id : new ArrayList<>(state.active.keySet())) {
            List<InstanceState> instances = state.active.get(id);
            if (instances.removeIf(value -> value.expiresAtEpochMs <= now)) changed = true;
            if (instances.isEmpty()) {
                state.active.remove(id);
                ProductionContentCatalog.StatusEntry definition = definition(id);
                if (definition != null) clearVisual(target, definition);
            }
        }
        if (hardBefore && !containsHardControl(state)) state.lastHardControlEndedAtEpochMs = now;
        return changed;
    }

    private double prepareHardControl(TargetState state, long now) {
        if (state.hardChainStartedAtEpochMs == 0L
                || (!containsHardControl(state) && now - state.lastHardControlEndedAtEpochMs >= CONTROL_RESET_MILLIS)) {
            state.hardChainStartedAtEpochMs = now;
            state.hardChainStage = 0;
        }
        int nextStage = state.hardChainStage;
        if (now - state.hardChainStartedAtEpochMs >= 3_000L) nextStage++;
        double multiplier = StatusRuntimePolicy.hardControlMultiplier(nextStage);
        if (multiplier > 0.0) state.hardChainStage = nextStage;
        return multiplier;
    }

    private double prepareActionLock(TargetState state, long now) {
        if (state.actionLockResetAtEpochMs <= now) state.actionLockStage = 0;
        int nextStage = state.actionLockStage + 1;
        double multiplier = StatusRuntimePolicy.actionLockMultiplier(nextStage);
        if (multiplier > 0.0) {
            state.actionLockStage = nextStage;
            state.actionLockResetAtEpochMs = now + CONTROL_RESET_MILLIS;
        }
        return multiplier;
    }

    private boolean hasCapacity(TargetState state, ProductionContentCatalog.StatusEntry candidate) {
        int total = state.active.values().stream().mapToInt(List::size).sum();
        int dots = state.active.entrySet().stream().filter(entry -> {
            ProductionContentCatalog.StatusEntry definition = definition(entry.getKey());
            return definition != null && definition.tags().contains("DOT");
        }).mapToInt(entry -> entry.getValue().size()).sum();
        if (state.active.containsKey(candidate.id())) return true;
        return total < STANDARD_INSTANCE_CAP && (!candidate.tags().contains("DOT") || dots < STANDARD_DOT_CAP);
    }

    private void clearTransientControls(LivingEntity target) {
        TargetState state = state(target);
        for (String id : new ArrayList<>(state.active.keySet())) {
            ProductionContentCatalog.StatusEntry definition = definition(id);
            if (definition != null && (isHardControl(definition) || definition.tags().contains("ACTION_LOCK")
                    || definition.tags().contains("ROOT"))) {
                state.active.remove(id);
                clearVisual(target, definition);
            }
        }
        resetControlState(state);
        save(target, state);
    }

    private void removeHardControls(LivingEntity target, TargetState state) {
        for (String id : new ArrayList<>(state.active.keySet())) {
            ProductionContentCatalog.StatusEntry definition = definition(id);
            if (definition != null && isHardControl(definition)) {
                state.active.remove(id);
                clearVisual(target, definition);
            }
        }
    }

    private void resetControlState(TargetState state) {
        state.hardChainStartedAtEpochMs = 0L;
        state.hardChainStage = 0;
        state.lastHardControlEndedAtEpochMs = 0L;
        state.actionLockStage = 0;
        state.actionLockResetAtEpochMs = 0L;
        state.controlImmunityUntilEpochMs = 0L;
        state.allStatusImmunityUntilEpochMs = 0L;
    }

    private void applyVisual(LivingEntity target, ProductionContentCatalog.StatusEntry definition,
                             long durationMillis, int stacks) {
        int ticks = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (durationMillis + 49L) / 50L));
        if (!(target instanceof Player player)) {
            if ("BURN".equals(definition.id())) target.setFireTicks(Math.max(target.getFireTicks(), ticks));
            if ("MARK".equals(definition.id())) target.setGlowing(true);
            return;
        }
        switch (definition.id()) {
            case "ROOT" -> player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 10, true, true));
            case "SLOW" -> player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks,
                    Math.max(0, Math.min(4, (int) Math.ceil(Math.max(0.0, definition.baseStrength()) * 5.0) - 1)), true, true));
            case "WEAKNESS" -> player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 0, true, true));
            case "POISON" -> player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, ticks,
                    Math.max(0, Math.min(4, stacks - 1)), true, true));
            case "BLEED" -> player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, ticks,
                    Math.max(0, Math.min(2, stacks - 1)), true, true));
            case "BLIND" -> player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, true, true));
            case "BURN" -> player.setFireTicks(Math.max(player.getFireTicks(), ticks));
            case "MARK" -> player.setGlowing(true);
            default -> { }
        }
    }

    private void clearVisual(LivingEntity target, ProductionContentCatalog.StatusEntry definition) {
        if (!(target instanceof Player player)) {
            if ("BURN".equals(definition.id())) target.setFireTicks(0);
            if ("MARK".equals(definition.id())) target.setGlowing(false);
            return;
        }
        switch (definition.id()) {
            case "ROOT", "SLOW" -> player.removePotionEffect(PotionEffectType.SLOWNESS);
            case "WEAKNESS" -> player.removePotionEffect(PotionEffectType.WEAKNESS);
            case "POISON" -> player.removePotionEffect(PotionEffectType.POISON);
            case "BLEED" -> player.removePotionEffect(PotionEffectType.WITHER);
            case "BLIND" -> player.removePotionEffect(PotionEffectType.BLINDNESS);
            case "BURN" -> player.setFireTicks(0);
            case "MARK" -> player.setGlowing(false);
            default -> { }
        }
    }

    private TargetState state(LivingEntity target) {
        TargetState cached = states.get(target.getUniqueId());
        if (cached != null) return cached;
        TargetState loaded = null;
        String json = target.getPersistentDataContainer().get(stateKey, PersistentDataType.STRING);
        if (json != null && !json.isBlank()) {
            try {
                loaded = gson.fromJson(json, TargetState.class);
            } catch (RuntimeException ignored) {
                loaded = null;
            }
        }
        if (loaded == null) loaded = new TargetState();
        if (loaded.active == null) loaded.active = new LinkedHashMap<>();
        states.put(target.getUniqueId(), loaded);
        if (!loaded.active.isEmpty()) tracked.add(target.getUniqueId());
        return loaded;
    }

    private void save(LivingEntity target, TargetState state) {
        if (isStateEmpty(state, runs.clockNowMillis())) {
            target.getPersistentDataContainer().remove(stateKey);
            states.remove(target.getUniqueId());
            tracked.remove(target.getUniqueId());
            return;
        }
        target.getPersistentDataContainer().set(stateKey, PersistentDataType.STRING, gson.toJson(state));
        states.put(target.getUniqueId(), state);
        tracked.add(target.getUniqueId());
    }

    private boolean isStateEmpty(TargetState state, long now) {
        return state.active.isEmpty() && state.allStatusImmunityUntilEpochMs <= now
                && state.controlImmunityUntilEpochMs <= now && state.actionLockResetAtEpochMs <= now
                && state.lastHardControlEndedAtEpochMs <= 0L;
    }

    private long latestExpiry(TargetState state) {
        long latest = Math.max(state.allStatusImmunityUntilEpochMs, state.controlImmunityUntilEpochMs);
        for (List<InstanceState> instances : state.active.values()) {
            for (InstanceState instance : instances) latest = Math.max(latest, instance.expiresAtEpochMs);
        }
        return latest;
    }

    private ProductionContentCatalog.StatusEntry definition(String rawStatusId) {
        if (rawStatusId == null || rawStatusId.isBlank()) return null;
        String normalized = rawStatusId.toUpperCase(Locale.ROOT).replace('-', '_');
        if ("ARMOR_SHRED".equals(normalized)) normalized = "ARMOR_BREAK";
        ProductionContentCatalog.StatusEntry direct = production.statusesById().get(normalized);
        if (direct != null) return direct;
        for (ProductionContentCatalog.StatusEntry entry : production.statusesById().values()) {
            if (entry.canonicalId().equals(normalized)) return entry;
        }
        return null;
    }

    private double sourceHitBonus(UUID sourceEntityId) {
        if (sourceEntityId == null) return 0.0;
        Player player = Bukkit.getPlayer(sourceEntityId);
        return player == null ? 0.0 : Math.max(0.0, equipment.activeStat(player, "HIT")) / 100.0;
    }

    private double targetResistance(LivingEntity target) {
        return target instanceof Player player ? Math.max(0.0, equipment.activeStat(player, "RES")) : 0.0;
    }

    private double targetTenacity(LivingEntity target) {
        return target instanceof Player player ? Math.max(0.0, equipment.activeStat(player, "TENACITY")) : 0.0;
    }

    private static boolean isHardControl(ProductionContentCatalog.StatusEntry definition) {
        return definition.tags().contains("HARD_CC") || definition.tags().contains("ROOT");
    }

    private boolean containsHardControl(TargetState state) {
        return state.active.keySet().stream().map(this::definition).anyMatch(value -> value != null && isHardControl(value));
    }

    private static double deterministicRoll(String executionId, UUID target, String statusId) {
        String seed = (executionId == null ? "" : executionId) + ":" + target + ":" + statusId;
        return Math.floorMod(seed.hashCode(), 10_000) / 10_000.0;
    }

    private static InstanceState instance(String id, String sourceEntity, String sourceId,
                                          long now, long expires, double strength) {
        InstanceState value = new InstanceState();
        value.instanceId = UUID.randomUUID().toString();
        value.statusId = id;
        value.sourceEntityId = sourceEntity;
        value.sourceId = sourceId;
        value.appliedAtEpochMs = now;
        value.expiresAtEpochMs = expires;
        value.strength = strength;
        value.stacks = 1;
        return value;
    }

    private void telemetry(String type, String statusId, LivingEntity target, String sourceId, String extra) {
        runs.current().ifPresent(run -> telemetry.event(run.runId, type,
                "{\"statusId\":\"" + statusId + "\",\"target\":\"" + target.getUniqueId()
                        + "\",\"sourceId\":\"" + escape(sourceId) + "\"," + extra + "}"));
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public enum TargetGrade { PLAYER, NORMAL, ELITE, BOSS }

    public record ApplyResult(boolean applied, boolean convertedToBreak, String reason,
                              double breakFraction, long durationMillis, int stacks) {
        static ApplyResult rejected(String reason) {
            return new ApplyResult(false, false, reason, 0.0, 0L, 0);
        }

        static ApplyResult converted(double breakFraction) {
            return new ApplyResult(false, true, "BREAK_CONVERT", breakFraction, 0L, 0);
        }
    }

    private static final class TargetState {
        Map<String, List<InstanceState>> active = new LinkedHashMap<>();
        long hardChainStartedAtEpochMs;
        int hardChainStage;
        long lastHardControlEndedAtEpochMs;
        int actionLockStage;
        long actionLockResetAtEpochMs;
        long allStatusImmunityUntilEpochMs;
        long controlImmunityUntilEpochMs;
    }

    private static final class InstanceState {
        String instanceId;
        String statusId;
        String sourceEntityId;
        String sourceId;
        long appliedAtEpochMs;
        long expiresAtEpochMs;
        double strength;
        int stacks;
    }

    private record StackResult(boolean changed, int stacks) { }
    private record Summary(String id, InstanceState instance) { }
}
