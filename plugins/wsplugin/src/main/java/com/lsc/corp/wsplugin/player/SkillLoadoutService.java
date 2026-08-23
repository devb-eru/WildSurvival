package com.lsc.corp.wsplugin.player;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.content.ProductionContentCatalog;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class SkillLoadoutService implements Listener {
    private static final int[] EQUIPPED = {11, 13, 15};
    private static final int[] COMMON_EQUIPPED = {19, 21, 23, 25};
    private static final int[] WEAPON_CATALOGUE = {28, 30, 32, 34};
    private static final int[] COMMON_CATALOGUE = {36, 37, 38, 39, 40, 41, 42, 43, 44, 45};
    private final RunService runs;
    private final PrototypeContent content;
    private final ProductionContentCatalog production;
    private final EquipmentService equipment;
    private final Map<String, PrototypeContent.SkillDefinition> runtimeSkills;

    public SkillLoadoutService(RunService runs, PrototypeContent content, ProductionContentCatalog production,
                               EquipmentService equipment) {
        this.runs = runs;
        this.content = content;
        this.production = production;
        this.equipment = equipment;
        this.runtimeSkills = production.skills().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                ProductionContentCatalog.SkillEntry::id, SkillLoadoutService::runtimeSkill));
    }

    public PrototypeContent.SkillDefinition resolve(Player player, int slot) {
        String weaponId = equipment.resolveWeaponId(player);
        ensureDefaults(player, weaponId);
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return null;
        String id = state.weaponSkillLoadouts.getOrDefault(weaponId, Map.of()).get(slot);
        if (id == null) return null;
        PrototypeContent.SkillDefinition skill = runtimeSkills.get(id);
        if (skill == null) return null;
        return skill.weaponIds().contains(weaponId) ? skill : null;
    }

    public PrototypeContent.SkillDefinition resolveCommon(Player player, int slot) {
        ensureCommonDefaults(player);
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || state.commonSkillLoadout == null) return null;
        String id = state.commonSkillLoadout.get(slot);
        ProductionContentCatalog.SkillEntry entry = production.skillsById().get(id);
        return entry != null && entry.commonActive() ? runtimeSkills.get(id) : null;
    }

    public int cooldownTicks(String skillId) {
        ProductionContentCatalog.SkillEntry entry = production.skillsById().get(skillId);
        return entry == null ? 0 : entry.cooldownTicks();
    }

    public String consumableId(String skillId) {
        ProductionContentCatalog.SkillEntry entry = production.skillsById().get(skillId);
        return entry == null ? "" : entry.consumableId();
    }

    public void open(Player player) {
        if (!runs.isMember(player)) { player.sendMessage(ChatColor.RED + "현재 회차 멤버가 아닙니다."); return; }
        String weaponId = equipment.resolveWeaponId(player);
        ensureDefaults(player, weaponId);
        SkillHolder holder = new SkillHolder(player.getUniqueId(), weaponId);
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_PURPLE + "스킬 · 무기/공용");
        render(inventory, holder);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SkillHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        if (!holder.weaponId.equals(equipment.resolveWeaponId(player))) {
            player.sendMessage(ChatColor.RED + "주무기가 변경되어 스킬 화면을 다시 엽니다."); open(player); return;
        }
        if (event.getRawSlot() == 49) { player.closeInventory(); return; }
        for (int i = 0; i < EQUIPPED.length; i++) if (event.getRawSlot() == EQUIPPED[i]) {
            int targetSlot = i + 1;
            runs.mutate(run -> {
                RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                Map<Integer, String> loadout = state.weaponSkillLoadouts.computeIfAbsent(holder.weaponId, ignored -> new LinkedHashMap<>());
                state.productionWeaponSkillLoadoutsInitialized = true;
                if (holder.selectedWeaponSkillId == null) loadout.remove(targetSlot);
                else {
                    loadout.entrySet().removeIf(entry -> holder.selectedWeaponSkillId.equals(entry.getValue()));
                    loadout.put(targetSlot, holder.selectedWeaponSkillId);
                    state.tutorialSignals.add("CONFIGURED_SKILL");
                }
            });
            holder.selectedWeaponSkillId = null;
            render(event.getInventory(), holder);
            return;
        }
        for (int i = 0; i < COMMON_EQUIPPED.length; i++) if (event.getRawSlot() == COMMON_EQUIPPED[i]) {
            int targetSlot = i + 1;
            runs.mutate(run -> {
                RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString());
                if (state.commonSkillLoadout == null) state.commonSkillLoadout = new LinkedHashMap<>();
                state.productionCommonSkillLoadoutInitialized = true;
                if (holder.selectedCommonSkillId == null) state.commonSkillLoadout.remove(targetSlot);
                else {
                    state.commonSkillLoadout.entrySet().removeIf(entry -> holder.selectedCommonSkillId.equals(entry.getValue()));
                    state.commonSkillLoadout.put(targetSlot, holder.selectedCommonSkillId);
                    state.tutorialSignals.add("CONFIGURED_COMMON_SKILL");
                }
            });
            holder.selectedCommonSkillId = null;
            render(event.getInventory(), holder);
            return;
        }
        for (int i = 0; i < WEAPON_CATALOGUE.length; i++) if (event.getRawSlot() == WEAPON_CATALOGUE[i]) {
            List<PrototypeContent.SkillDefinition> candidates = compatible(player, holder.weaponId);
            if (i < candidates.size()) holder.selectedWeaponSkillId = candidates.get(i).id();
            render(event.getInventory(), holder);
            return;
        }
        for (int i = 0; i < COMMON_CATALOGUE.length; i++) if (event.getRawSlot() == COMMON_CATALOGUE[i]) {
            List<PrototypeContent.SkillDefinition> candidates = commonSkills(player);
            if (i < candidates.size()) holder.selectedCommonSkillId = candidates.get(i).id();
            render(event.getInventory(), holder);
            return;
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SkillHolder) event.setCancelled(true);
    }

    private void ensureDefaults(Player player, String weaponId) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return;
        List<PrototypeContent.SkillDefinition> compatible = compatible(player, weaponId);
        Set<String> allowed = compatible.stream().map(PrototypeContent.SkillDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        boolean initialize = !state.productionWeaponSkillLoadoutsInitialized
                || state.weaponSkillLoadouts == null || !state.weaponSkillLoadouts.containsKey(weaponId);
        runs.mutate(run -> {
            RunSnapshot.PlayerState mutable = run.players.get(player.getUniqueId().toString());
            if (mutable.weaponSkillLoadouts == null) mutable.weaponSkillLoadouts = new LinkedHashMap<>();
            Map<Integer, String> loadout = mutable.weaponSkillLoadouts
                    .computeIfAbsent(weaponId, ignored -> new LinkedHashMap<>());
            loadout.entrySet().removeIf(entry -> entry.getKey() < 1 || entry.getKey() > 3 || !allowed.contains(entry.getValue()));
            if (initialize) {
                for (PrototypeContent.SkillDefinition skill : compatible) {
                    if (loadout.size() >= 3) break;
                    if (loadout.containsValue(skill.id())) continue;
                    for (int slot = 1; slot <= 3; slot++) if (!loadout.containsKey(slot)) {
                        loadout.put(slot, skill.id());
                        break;
                    }
                }
            }
            mutable.productionWeaponSkillLoadoutsInitialized = true;
        });
    }

    private void ensureCommonDefaults(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return;
        List<PrototypeContent.SkillDefinition> candidates = commonSkills(player);
        Set<String> allowed = candidates.stream().map(PrototypeContent.SkillDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        boolean initialize = !state.productionCommonSkillLoadoutInitialized;
        runs.mutate(run -> {
            RunSnapshot.PlayerState mutable = run.players.get(player.getUniqueId().toString());
            if (mutable.commonSkillLoadout == null) mutable.commonSkillLoadout = new LinkedHashMap<>();
            mutable.commonSkillLoadout.entrySet().removeIf(entry -> entry.getKey() < 1 || entry.getKey() > 4
                    || !allowed.contains(entry.getValue()));
            if (initialize) {
                for (PrototypeContent.SkillDefinition skill : candidates) {
                    if (mutable.commonSkillLoadout.size() >= 4) break;
                    if (mutable.commonSkillLoadout.containsValue(skill.id())) continue;
                    for (int slot = 1; slot <= 4; slot++) if (!mutable.commonSkillLoadout.containsKey(slot)) {
                        mutable.commonSkillLoadout.put(slot, skill.id());
                        break;
                    }
                }
            }
            mutable.productionCommonSkillLoadoutInitialized = true;
        });
    }

    private void render(Inventory inventory, SkillHolder holder) {
        ItemStack border = named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, border);
        RunSnapshot.PlayerState state = runs.playerState(holder.owner).orElseThrow();
        ensureCommonDefaults(Bukkit.getPlayer(holder.owner));
        state = runs.playerState(holder.owner).orElseThrow();
        Map<Integer, String> loadout = state.weaponSkillLoadouts.getOrDefault(holder.weaponId, Map.of());
        inventory.setItem(4, named(Material.BLAZE_POWDER, ChatColor.LIGHT_PURPLE + content.weapon(holder.weaponId).name() + " 스킬",
                List.of(ChatColor.WHITE + "무기 스킬과 공용 액티브는 별도 입력입니다.",
                        ChatColor.GRAY + (state.detailedTooltips ? "상세 설명 모드" : "간단 설명 모드"))));
        inventory.setItem(2, named(Material.IRON_SWORD, ChatColor.AQUA + "무기 스킬 W1~W3",
                List.of(ChatColor.GRAY + "후보 선택 후 W 슬롯 클릭", ChatColor.GRAY + "빈 선택으로 클릭하면 해제")));
        inventory.setItem(6, named(Material.NETHER_STAR, ChatColor.GOLD + "공용 액티브 C1~C4",
                List.of(ChatColor.GRAY + "slot 0 전투 자세에서 Shift+2~5", ChatColor.GRAY + "후보 선택 후 C 슬롯 클릭")));
        String[] inputs = {"R", "Shift+L", "Shift+R / F"};
        for (int i = 0; i < EQUIPPED.length; i++) {
            String skillId = loadout.get(i + 1);
            inventory.setItem(EQUIPPED[i], skillId == null
                    ? named(Material.BARRIER, ChatColor.RED + "W" + (i + 1) + " 비어 있음", List.of(ChatColor.GRAY + inputs[i]))
                    : skillIcon(runtimeSkills.get(skillId), ChatColor.GREEN + "W" + (i + 1) + " " + runtimeSkills.get(skillId).name(),
                            List.of(ChatColor.GRAY + "입력: " + inputs[i], ChatColor.YELLOW + "장착됨 · 클릭하면 해제"),
                            state.detailedTooltips, true));
        }
        Map<Integer, String> commonLoadout = state.commonSkillLoadout == null ? Map.of() : state.commonSkillLoadout;
        for (int i = 0; i < COMMON_EQUIPPED.length; i++) {
            String skillId = commonLoadout.get(i + 1);
            PrototypeContent.SkillDefinition skill = runtimeSkills.get(skillId);
            inventory.setItem(COMMON_EQUIPPED[i], skill == null
                    ? named(Material.BARRIER, ChatColor.RED + "C" + (i + 1) + " 비어 있음",
                    List.of(ChatColor.GRAY + "입력: Shift+" + (i + 2)))
                    : skillIcon(skill, ChatColor.GOLD + "C" + (i + 1) + " " + skill.name(),
                    List.of(ChatColor.GRAY + "입력: Shift+" + (i + 2), ChatColor.YELLOW + "장착됨 · 클릭하면 해제"),
                    state.detailedTooltips, true));
        }
        List<PrototypeContent.SkillDefinition> weaponCandidates = compatible(Bukkit.getPlayer(holder.owner), holder.weaponId);
        for (int i = 0; i < weaponCandidates.size() && i < WEAPON_CATALOGUE.length; i++) {
            PrototypeContent.SkillDefinition skill = weaponCandidates.get(i);
            boolean selected = skill.id().equals(holder.selectedWeaponSkillId);
            inventory.setItem(WEAPON_CATALOGUE[i], skillIcon(skill,
                    (selected ? ChatColor.YELLOW + "[선택] " : ChatColor.AQUA.toString()) + skill.name(),
                    List.of(selected ? ChatColor.YELLOW + "장착할 W 슬롯을 클릭하세요." : ChatColor.GRAY + "클릭하여 선택"),
                    state.detailedTooltips, false));
        }
        List<PrototypeContent.SkillDefinition> commonCandidates = commonSkills(Bukkit.getPlayer(holder.owner));
        for (int i = 0; i < commonCandidates.size() && i < COMMON_CATALOGUE.length; i++) {
            PrototypeContent.SkillDefinition skill = commonCandidates.get(i);
            boolean selected = skill.id().equals(holder.selectedCommonSkillId);
            inventory.setItem(COMMON_CATALOGUE[i], skillIcon(skill,
                    (selected ? ChatColor.YELLOW + "[C 선택] " : ChatColor.LIGHT_PURPLE.toString()) + skill.name(),
                    List.of(selected ? ChatColor.YELLOW + "장착할 C 슬롯을 클릭하세요." : ChatColor.GRAY + "공용 후보 · 클릭하여 선택"),
                    state.detailedTooltips, false));
        }
        inventory.setItem(49, named(Material.OAK_DOOR, ChatColor.RED + "닫기", List.of()));
    }

    private ItemStack skillIcon(PrototypeContent.SkillDefinition skill, String name, List<String> tail,
                                boolean detailed, boolean equipped) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + skill.description());
        if (detailed) {
            lore.add(ChatColor.GRAY + "AP " + Math.round(skill.apCost()) + " / 피해 x" + skill.damageCoefficient()
                    + " / 브레이크 " + Math.round(skill.breakDamage()));
            lore.add(ChatColor.DARK_GRAY + "ID: " + skill.id());
        }
        lore.addAll(tail);
        ItemStack icon = named(Material.BOOK, name, lore);
        return equipped ? glowing(icon) : icon;
    }

    private static ItemStack glowing(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private List<PrototypeContent.SkillDefinition> compatible(Player player, String weaponId) {
        int level = player == null ? 1 : runs.playerState(player.getUniqueId()).map(state -> state.level).orElse(1);
        return production.skills().stream().filter(ProductionContentCatalog.SkillEntry::weaponActive)
                .filter(skill -> weaponId.equals(skill.weaponClass()) && skill.unlockLevel() <= level)
                .map(skill -> runtimeSkills.get(skill.id())).toList();
    }

    private List<PrototypeContent.SkillDefinition> commonSkills(Player player) {
        int level = player == null ? 1 : runs.playerState(player.getUniqueId()).map(state -> state.level).orElse(1);
        return production.skills().stream().filter(ProductionContentCatalog.SkillEntry::commonActive)
                .filter(skill -> skill.unlockLevel() <= level).map(skill -> runtimeSkills.get(skill.id())).toList();
    }

    private static PrototypeContent.SkillDefinition runtimeSkill(ProductionContentCatalog.SkillEntry skill) {
        String particle = switch (skill.effect()) {
            case "BURN" -> "FLAME";
            case "POISON" -> "ENTITY_EFFECT";
            case "HEAL", "CLEANSE" -> "COMPOSTER";
            case "ROOT", "SLOW" -> "SNOWFLAKE";
            case "TRIDENT_TOGGLE" -> "ELECTRIC_SPARK";
            default -> "CRIT";
        };
        String sound = switch (skill.weaponClass()) {
            case "BOW" -> "ENTITY_ARROW_SHOOT";
            case "CROSSBOW" -> "ITEM_CROSSBOW_SHOOT";
            case "STAFF" -> "BLOCK_AMETHYST_BLOCK_RESONATE";
            case "TRIDENT" -> "ITEM_TRIDENT_THROW";
            default -> "ENTITY_PLAYER_ATTACK_STRONG";
        };
        return new PrototypeContent.SkillDefinition(skill.id(), skill.name(), List.of(skill.weaponClass()),
                skill.effect(), skill.apCost(), skill.damageCoefficient(), skill.breakDamage(), skill.range(),
                skill.arcDegrees(), skill.maxTargets(), particle, sound, skill.description());
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); return item;
    }

    private static final class SkillHolder implements InventoryHolder {
        private final UUID owner; private final String weaponId;
        private String selectedWeaponSkillId;
        private String selectedCommonSkillId;
        private SkillHolder(UUID owner, String weaponId) { this.owner = owner; this.weaponId = weaponId; }
        @Override public Inventory getInventory() { return null; }
    }
}
