package com.lsc.corp.wsplugin.ui;

import com.lsc.corp.wsplugin.economy.EconomyService;
import com.lsc.corp.wsplugin.economy.ItemCodexService;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.player.EquipmentService;
import com.lsc.corp.wsplugin.player.PlayerStatService;
import com.lsc.corp.wsplugin.player.SkillLoadoutService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import com.lsc.corp.wsplugin.tutorial.TutorialService;
import java.util.List;
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

public final class PlayerMenuService implements Listener {
    private final RunService runs;
    private final EconomyService economy;
    private final ItemCodexService codex;
    private final PlayerStatService stats;
    private final EquipmentService equipment;
    private final SkillLoadoutService skills;
    private final GrowthService growth;
    private final TutorialService tutorial;

    public PlayerMenuService(RunService runs, EconomyService economy, ItemCodexService codex,
                             PlayerStatService stats, EquipmentService equipment, SkillLoadoutService skills,
                             GrowthService growth, TutorialService tutorial) {
        this.runs = runs; this.economy = economy; this.codex = codex;
        this.stats = stats; this.equipment = equipment; this.growth = growth;
        this.skills = skills;
        this.tutorial = tutorial;
    }

    public void open(Player player) {
        if (!runs.isMember(player)) { player.sendMessage(ChatColor.RED + "현재 회차 멤버가 아닙니다."); return; }
        RunSnapshot run = runs.current().orElseThrow();
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        Inventory inventory = Bukkit.createInventory(new MenuHolder(player.getUniqueId()), 54,
                ChatColor.DARK_AQUA + "WildSurvival 플레이어 메뉴");
        inventory.setItem(4, named(Material.PLAYER_HEAD, ChatColor.GOLD + player.getName(), List.of(
                ChatColor.WHITE + "Lv." + state.level + "  AP " + Math.round(state.ap) + "/" + Math.round(state.maxAp),
                ChatColor.GRAY + "Shift+F로 열기")));
        inventory.setItem(19, named(Material.CRAFTING_TABLE, ChatColor.AQUA + "Craft",
                List.of(run.craftUnlocked ? ChatColor.GREEN + "해금됨" : ChatColor.RED + "원목 4개로 해금")));
        inventory.setItem(21, named(Material.KNOWLEDGE_BOOK, ChatColor.AQUA + "아이템 도감", List.of(ChatColor.GRAY + "고정 ID 항목 확인")));
        inventory.setItem(23, named(Material.NETHER_STAR, ChatColor.AQUA + "스탯 찍기", List.of(ChatColor.GRAY + "남은 포인트와 효과 확인")));
        inventory.setItem(25, named(Material.IRON_CHESTPLATE, ChatColor.AQUA + "장비 장착", List.of(ChatColor.GRAY + "주무기·보조·Q1~Q4")));
        inventory.setItem(27, named(Material.BLAZE_POWDER, ChatColor.LIGHT_PURPLE + "스킬",
                List.of(ChatColor.GRAY + "W1~W3 무기 스킬 · C1~C4 공용 액티브")));
        inventory.setItem(29, named(Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE + "증강",
                List.of(ChatColor.GRAY + "보유 개인·파티 증강 확인", ChatColor.YELLOW + "미선택 증강이 있으면 선택 화면 표시")));
        inventory.setItem(31, named(Material.BARREL, ChatColor.GREEN + "공용 자원 원장",
                List.of(run.sharedLedgerUnlocked ? ChatColor.GREEN + "저장소 가동 중" : ChatColor.RED + "공용 보급 저장소 제작·설치 필요")));
        inventory.setItem(33, named(Material.COMPARATOR, ChatColor.YELLOW + "설정",
                List.of(ChatColor.GRAY + "피해량 표시·도감/증강/스킬 설명")));
        inventory.setItem(35, named(Material.BOOK, ChatColor.GOLD + "초반 생존 길잡이", List.of(ChatColor.GRAY + "L키 Advancement 탭 안내")));
        inventory.setItem(49, named(Material.BARRIER, ChatColor.RED + "닫기", List.of()));
        player.openInventory(inventory);
    }

    public void openSettings(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        Inventory inventory = Bukkit.createInventory(new SettingsHolder(player.getUniqueId()), 27, ChatColor.DARK_GRAY + "WildSurvival 설정");
        inventory.setItem(11, toggle(Material.ARMOR_STAND, "피해량 표시", state.damageNumbersEnabled));
        inventory.setItem(15, toggle(Material.WRITABLE_BOOK, "상세 설명(도감·증강·스킬)", state.detailedTooltips));
        inventory.setItem(22, named(Material.OAK_DOOR, ChatColor.RED + "뒤로", List.of()));
        player.openInventory(inventory);
    }

    public void openCodex(Player player) { codex.open(player); }

    public void openStats(Player player) { stats.open(player); }

    public void openSkills(Player player) { skills.open(player); }

    public void openGuide(Player player) { tutorial.showGuide(player); }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
            switch (event.getRawSlot()) {
                case 19 -> economy.openCraft(player); case 21 -> codex.open(player); case 23 -> stats.open(player);
                case 25 -> equipment.open(player); case 27 -> skills.open(player); case 29 -> growth.openAugments(player);
                case 31 -> economy.openLedger(player); case 33 -> openSettings(player); case 35 -> tutorial.showGuide(player);
                case 49 -> player.closeInventory();
                default -> { }
            }
            return;
        }
        if (event.getInventory().getHolder() instanceof SettingsHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
            if (event.getRawSlot() == 11) runs.mutate(run -> {
                RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString()); state.damageNumbersEnabled = !state.damageNumbersEnabled;
            });
            else if (event.getRawSlot() == 15) runs.mutate(run -> {
                RunSnapshot.PlayerState state = run.players.get(player.getUniqueId().toString()); state.detailedTooltips = !state.detailedTooltips;
            });
            else if (event.getRawSlot() == 22) { open(player); return; }
            openSettings(player);
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder || event.getInventory().getHolder() instanceof SettingsHolder) event.setCancelled(true);
    }

    private ItemStack toggle(Material material, String name, boolean enabled) {
        return named(material, (enabled ? ChatColor.GREEN : ChatColor.RED) + name + ": " + (enabled ? "ON" : "OFF"), List.of(ChatColor.GRAY + "클릭하여 전환"));
    }
    private static ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); return item;
    }
    private static final class MenuHolder implements InventoryHolder {
        private final UUID owner; private MenuHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return null; }
    }
    private static final class SettingsHolder implements InventoryHolder {
        private final UUID owner; private SettingsHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return null; }
    }
}
