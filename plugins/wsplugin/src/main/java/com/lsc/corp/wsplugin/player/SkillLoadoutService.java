package com.lsc.corp.wsplugin.player;

import com.lsc.corp.wsplugin.content.PrototypeContent;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class SkillLoadoutService implements Listener {
    private static final int[] EQUIPPED = {11, 13, 15};
    private static final int[] CATALOGUE = {27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43};
    private final RunService runs;
    private final PrototypeContent content;
    private final EquipmentService equipment;

    public SkillLoadoutService(RunService runs, PrototypeContent content, EquipmentService equipment) {
        this.runs = runs;
        this.content = content;
        this.equipment = equipment;
    }

    public PrototypeContent.SkillDefinition resolve(Player player, int slot) {
        String weaponId = equipment.resolveWeaponId(player);
        ensureDefaults(player, weaponId);
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return null;
        String id = state.weaponSkillLoadouts.getOrDefault(weaponId, Map.of()).get(slot);
        if (id == null) return null;
        PrototypeContent.SkillDefinition skill = content.skill(id);
        return skill.weaponIds().contains(weaponId) ? skill : null;
    }

    public void open(Player player) {
        if (!runs.isMember(player)) { player.sendMessage(ChatColor.RED + "현재 회차 멤버가 아닙니다."); return; }
        String weaponId = equipment.resolveWeaponId(player);
        ensureDefaults(player, weaponId);
        SkillHolder holder = new SkillHolder(player.getUniqueId(), weaponId);
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.DARK_PURPLE + "무기 스킬 로드아웃");
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
                if (holder.selectedSkillId == null) loadout.remove(targetSlot);
                else {
                    loadout.entrySet().removeIf(entry -> holder.selectedSkillId.equals(entry.getValue()));
                    loadout.put(targetSlot, holder.selectedSkillId);
                    state.tutorialSignals.add("CONFIGURED_SKILL");
                }
            });
            holder.selectedSkillId = null;
            render(event.getInventory(), holder);
            return;
        }
        for (int i = 0; i < CATALOGUE.length; i++) if (event.getRawSlot() == CATALOGUE[i]) {
            List<PrototypeContent.SkillDefinition> skills = compatible(holder.weaponId);
            if (i < skills.size()) holder.selectedSkillId = skills.get(i).id();
            render(event.getInventory(), holder);
            return;
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SkillHolder) event.setCancelled(true);
    }

    private void ensureDefaults(Player player, String weaponId) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null || state.weaponSkillLoadouts.containsKey(weaponId)) return;
        List<PrototypeContent.SkillDefinition> compatible = compatible(weaponId);
        runs.mutate(run -> {
            Map<Integer, String> defaults = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(3, compatible.size()); i++) defaults.put(i + 1, compatible.get(i).id());
            run.players.get(player.getUniqueId().toString()).weaponSkillLoadouts.put(weaponId, defaults);
        });
    }

    private void render(Inventory inventory, SkillHolder holder) {
        ItemStack border = named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, border);
        RunSnapshot.PlayerState state = runs.playerState(holder.owner).orElseThrow();
        Map<Integer, String> loadout = state.weaponSkillLoadouts.getOrDefault(holder.weaponId, Map.of());
        inventory.setItem(4, named(Material.BLAZE_POWDER, ChatColor.LIGHT_PURPLE + content.weapon(holder.weaponId).name() + " 스킬",
                List.of(ChatColor.WHITE + "후보를 선택한 뒤 W 슬롯을 클릭", ChatColor.GRAY + "선택 없이 W 슬롯 클릭: 장착 해제")));
        String[] inputs = {"R", "Shift+L", "Shift+R / F"};
        for (int i = 0; i < EQUIPPED.length; i++) {
            String skillId = loadout.get(i + 1);
            inventory.setItem(EQUIPPED[i], skillId == null
                    ? named(Material.BARRIER, ChatColor.RED + "W" + (i + 1) + " 비어 있음", List.of(ChatColor.GRAY + inputs[i]))
                    : skillIcon(content.skill(skillId), ChatColor.GREEN + "W" + (i + 1) + " " + content.skill(skillId).name(),
                            List.of(ChatColor.GRAY + inputs[i], ChatColor.YELLOW + "선택 없이 클릭하면 해제")));
        }
        List<PrototypeContent.SkillDefinition> skills = compatible(holder.weaponId);
        for (int i = 0; i < skills.size() && i < CATALOGUE.length; i++) {
            PrototypeContent.SkillDefinition skill = skills.get(i);
            boolean selected = skill.id().equals(holder.selectedSkillId);
            inventory.setItem(CATALOGUE[i], skillIcon(skill,
                    (selected ? ChatColor.YELLOW + "[선택] " : ChatColor.AQUA.toString()) + skill.name(),
                    List.of(selected ? ChatColor.YELLOW + "장착할 W 슬롯을 클릭하세요." : ChatColor.GRAY + "클릭하여 선택")));
        }
        inventory.setItem(49, named(Material.OAK_DOOR, ChatColor.RED + "닫기", List.of()));
    }

    private ItemStack skillIcon(PrototypeContent.SkillDefinition skill, String name, List<String> tail) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.WHITE + skill.description());
        lore.add(ChatColor.GRAY + "AP " + Math.round(skill.apCost()) + " / 피해 x" + skill.damageCoefficient()
                + " / 브레이크 " + Math.round(skill.breakDamage()));
        lore.add(ChatColor.DARK_GRAY + "ID: " + skill.id());
        lore.addAll(tail);
        return named(Material.ENCHANTED_BOOK, name, lore);
    }

    private List<PrototypeContent.SkillDefinition> compatible(String weaponId) {
        return content.skills().stream().filter(skill -> skill.weaponIds().contains(weaponId)).toList();
    }

    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); return item;
    }

    private static final class SkillHolder implements InventoryHolder {
        private final UUID owner; private final String weaponId; private String selectedSkillId;
        private SkillHolder(UUID owner, String weaponId) { this.owner = owner; this.weaponId = weaponId; }
        @Override public Inventory getInventory() { return null; }
    }
}
