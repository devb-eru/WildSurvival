package com.lsc.corp.wsplugin.tutorial;

import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

@SuppressWarnings("deprecation")
public final class TutorialService implements Listener {
    private final JavaPlugin plugin;
    private final RunService runs;
    private final List<Quest> quests = List.of(
            new Quest("root", "WildSurvival 생존 기록", "L키를 눌러 Day 10까지의 초반 길잡이를 확인하세요.", Material.COMPASS, null, "ROOT", "task"),
            new Quest("gather_wood", "첫 번째 원목", "나무를 베어 [WS] 목재를 획득하세요. 등록 자원은 한 종류만 드롭됩니다.", Material.OAK_LOG, "root", "WOOD", "task"),
            new Quest("unlock_craft", "제작 지식 복구", "아무 원목 4개를 준비하고 Shift+F → Craft에서 파티 제작 기능을 해금하세요.", Material.CRAFTING_TABLE, "gather_wood", "CRAFT", "task"),
            new Quest("crude_pickaxe", "손보다 나은 도구", "목재 3·섬유 2로 급조 곡괭이를 3×3 조합하세요.", Material.WOODEN_PICKAXE, "unlock_craft", "CRUDE_PICK", "task"),
            new Quest("gather_stone", "석재 확보", "급조 곡괭이를 슬롯 1~8에서 사용해 석재를 모으세요.", Material.COBBLESTONE, "crude_pickaxe", "STONE", "task"),
            new Quest("stone_pickaxe", "광맥을 향해", "목재 2·석재 3으로 석재 채집 곡괭이를 제작하세요.", Material.STONE_PICKAXE, "gather_stone", "STONE_PICK", "task"),
            new Quest("gather_iron", "철기 시대", "석재 채집 곡괭이로 철광석을 캐서 [WS] 철을 획득하세요.", Material.RAW_IRON, "stone_pickaxe", "IRON", "task"),
            new Quest("craft_weapon", "첫 전투 장비", "돌날 전투 도끼·검·활·전투 곡괭이 중 하나를 제작하세요.", Material.IRON_SWORD, "gather_iron", "WEAPON", "task"),
            new Quest("equip_weapon", "전투 자세", "Shift+F → 장비에서 주무기를 장착해 슬롯 0에 동기화하세요.", Material.IRON_CHESTPLATE, "craft_weapon", "EQUIP", "task"),
            new Quest("configure_skill", "나만의 기술 구성", "플레이어 메뉴 → 무기 스킬에서 후보를 선택하고 W1~W3에 장착하세요.", Material.ENCHANTED_BOOK, "equip_weapon", "CONFIGURE_SKILL", "task"),
            new Quest("use_skill", "AP를 힘으로", "슬롯 0 전투 자세에서 R·Shift+L·Shift+R 중 하나로 스킬을 사용하세요.", Material.BLAZE_POWDER, "configure_skill", "USE_SKILL", "task"),
            new Quest("build_depot", "함께 쓰는 보급", "목재 4·석재 3·철 2로 공용 보급 저장소를 제작하세요.", Material.BARREL, "use_skill", "DEPOT", "goal"),
            new Quest("level_three", "첫 성장 분기", "활동 경험치를 모아 레벨 3에 도달하세요.", Material.EXPERIENCE_BOTTLE, "build_depot", "LEVEL3", "task"),
            new Quest("first_augment", "실버 증강", "레벨 3 개인 증강 3개 중 하나를 선택하세요.", Material.IRON_NUGGET, "level_three", "AUGMENT", "goal"),
            new Quest("day_three", "Day 3: 원거리 압박", "활·엄폐·회피를 준비하고 Day 3에 진입하세요.", Material.BOW, "first_augment", "DAY3", "goal"),
            new Quest("day_six", "Day 6: 오염 파열", "둔기·전투 곡괭이의 브레이크와 중화제를 준비해 Day 6에 진입하세요.", Material.MACE, "day_three", "DAY6", "goal"),
            new Quest("day_ten", "Day 10: 최종 준비", "철제 도구·소모품·스킬 로드아웃을 점검하고 Day 10에 진입하세요.", Material.RECOVERY_COMPASS, "day_six", "DAY10", "challenge"),
            new Quest("boss", "첫 단추", "파티와 함께 공명 추적체를 처치하고 프로토타입 생존 루프를 완주하세요.", Material.NETHER_STAR, "day_ten", "BOSS", "challenge")
    );
    private final Map<String, Advancement> advancements = new LinkedHashMap<>();
    private BukkitTask task;

    public TutorialService(JavaPlugin plugin, RunService runs) {
        this.plugin = plugin;
        this.runs = runs;
    }

    public void start() {
        loadAdvancements();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) { task.cancel(); task = null; }
        for (Quest quest : quests) Bukkit.getUnsafe().removeAdvancement(key(quest.id));
        advancements.clear();
    }

    public void showGuide(Player player) {
        if (!runs.isMember(player)) { player.sendMessage(ChatColor.RED + "현재 회차 멤버가 아닙니다."); return; }
        syncCompleted(player);
        player.sendTitle(ChatColor.GOLD + "초반 생존 길잡이", ChatColor.YELLOW + "L키 → WildSurvival 탭", 5, 50, 10);
        player.sendMessage(ChatColor.AQUA + "[길잡이] L키를 눌러 현재 목표와 다음 제작 단계를 확인하세요.");
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (runs.isMember(event.getPlayer())) { syncCompleted(event.getPlayer()); tickPlayer(event.getPlayer()); }
        }, 20L);
    }

    private void loadAdvancements() {
        for (Quest quest : quests) {
            NamespacedKey key = key(quest.id);
            if (Bukkit.getAdvancement(key) != null) Bukkit.getUnsafe().removeAdvancement(key);
            Advancement advancement = Bukkit.getUnsafe().loadAdvancement(key, json(quest));
            if (advancement == null) throw new IllegalStateException("Cannot register tutorial advancement " + key);
            advancements.put(quest.id, advancement);
        }
    }

    private void tick() {
        for (Player player : runs.onlineMembers()) tickPlayer(player);
    }

    private void tickPlayer(Player player) {
        RunSnapshot run = runs.current().orElse(null);
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (run == null || state == null) return;
        syncCompleted(player);
        for (Quest quest : quests) {
            if (state.completedTutorialQuests.contains(quest.id)) continue;
            if (quest.parent != null && !state.completedTutorialQuests.contains(quest.parent)) break;
            if (!condition(quest.condition, run, state)) break;
            Advancement advancement = advancements.get(quest.id);
            if (advancement == null) break;
            player.getAdvancementProgress(advancement).awardCriteria("done");
            runs.mutate(snapshot -> snapshot.players.get(player.getUniqueId().toString()).completedTutorialQuests.add(quest.id));
            break;
        }
    }

    private void syncCompleted(Player player) {
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElse(null);
        if (state == null) return;
        for (String id : state.completedTutorialQuests) {
            Advancement advancement = advancements.get(id);
            if (advancement != null) player.getAdvancementProgress(advancement).awardCriteria("done");
        }
    }

    private boolean condition(String condition, RunSnapshot run, RunSnapshot.PlayerState state) {
        return switch (condition) {
            case "ROOT" -> true;
            case "WOOD" -> state.discoveredItemIds.contains("WSR-WOOD");
            case "CRAFT" -> run.craftUnlocked;
            case "CRUDE_PICK" -> state.discoveredItemIds.contains("TOOL-CRUDE-PICKAXE");
            case "STONE" -> state.discoveredItemIds.contains("WSR-STONE");
            case "STONE_PICK" -> state.discoveredItemIds.contains("TOOL-STONE-PICKAXE");
            case "IRON" -> state.discoveredItemIds.contains("WSR-IRON");
            case "WEAPON" -> state.ownedEquipment.stream().anyMatch(id -> !"UNARMED".equals(id));
            case "EQUIP" -> state.mainWeaponId != null;
            case "CONFIGURE_SKILL" -> state.tutorialSignals.contains("CONFIGURED_SKILL");
            case "USE_SKILL" -> state.tutorialSignals.contains("USED_SKILL");
            case "DEPOT" -> run.sharedLedgerUnlocked && run.facility != null && run.facility.active;
            case "LEVEL3" -> state.level >= 3;
            case "AUGMENT" -> !state.personalAugments.isEmpty();
            case "DAY3" -> run.day >= 3;
            case "DAY6" -> run.day >= 6;
            case "DAY10" -> run.day >= 10;
            case "BOSS" -> run.boss != null && run.boss.rewardCommitted;
            default -> false;
        };
    }

    private String json(Quest quest) {
        String parent = quest.parent == null ? "" : "\"parent\":\"" + key(quest.parent) + "\",";
        String background = quest.parent == null
                ? "\"background\":\"minecraft:textures/gui/advancements/backgrounds/stone.png\"," : "";
        return "{" + parent + "\"display\":{" +
                "\"icon\":{\"id\":\"minecraft:" + quest.icon.name().toLowerCase(java.util.Locale.ROOT) + "\"}," +
                "\"title\":{\"text\":\"" + escape(quest.title) + "\",\"color\":\"gold\"}," +
                "\"description\":{\"text\":\"" + escape(quest.description) + "\",\"color\":\"white\"}," +
                background + "\"frame\":\"" + quest.frame + "\",\"show_toast\":true,\"announce_to_chat\":false,\"hidden\":false}," +
                "\"criteria\":{\"done\":{\"trigger\":\"minecraft:impossible\"}}}";
    }

    private NamespacedKey key(String id) { return new NamespacedKey(plugin, "tutorial/" + id); }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private record Quest(String id, String title, String description, Material icon, String parent, String condition, String frame) { }
}
