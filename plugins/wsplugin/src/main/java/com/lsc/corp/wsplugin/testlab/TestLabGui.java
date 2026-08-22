package com.lsc.corp.wsplugin.testlab;

import com.lsc.corp.wsplugin.combat.CombatService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class TestLabGui implements Listener {
    private final JavaPlugin plugin;
    private final TestLabService lab;
    private final TestScenarioService scenarios;
    private final VirtualPartyService virtualParty;
    private final RunService runs;

    public TestLabGui(JavaPlugin plugin, TestLabService lab, TestScenarioService scenarios,
                      VirtualPartyService virtualParty, RunService runs) {
        this.plugin = plugin;
        this.lab = lab;
        this.scenarios = scenarios;
        this.virtualParty = virtualParty;
        this.runs = runs;
    }

    public void open(Player player) {
        if (!lab.owner(player)) {
            throw new IllegalStateException("Enter Test Lab with /ws test enter first");
        }
        RunSnapshot run = runs.current().orElseThrow();
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        Inventory inventory = Bukkit.createInventory(new Holder(player.getUniqueId()), 54,
                ChatColor.DARK_AQUA + "WildSurvival Test Lab");
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);
        inventory.setItem(0, item(Material.RECOVERY_COMPASS, ChatColor.AQUA + "세션 상태", List.of(
                ChatColor.WHITE + "Day " + run.day + " / Lv." + state.level,
                ChatColor.GRAY + "가상 파티 " + run.test.virtualPartySize + "인",
                ChatColor.GRAY + "Seed " + run.test.deterministicSeed,
                ChatColor.GRAY + (run.test.timeFrozen ? "시간 정지" : "시간 x" + run.test.timeScale))));
        inventory.setItem(2, item(Material.COMPARATOR, ChatColor.LIGHT_PURPLE + "프리셋: " + run.test.activePreset,
                List.of(ChatColor.YELLOW + "클릭: 다음 프리셋")));
        inventory.setItem(4, item(Material.COMPASS, ChatColor.GOLD + "시나리오: " + run.test.activeScenario,
                List.of(ChatColor.YELLOW + "클릭: 다음 시나리오 실행")));
        inventory.setItem(6, item(Material.PLAYER_HEAD, ChatColor.GREEN + "가상 파티 " + run.test.virtualPartySize + "인",
                List.of(ChatColor.YELLOW + "좌클릭 +1 / 우클릭 -1")));
        inventory.setItem(8, item(run.test.timeFrozen ? Material.CLOCK : Material.SUNFLOWER,
                ChatColor.YELLOW + (run.test.timeFrozen ? "논리 시간 정지" : "논리 시간 진행"),
                List.of(ChatColor.YELLOW + "클릭하여 전환")));

        inventory.setItem(10, item(Material.EXPERIENCE_BOTTLE, ChatColor.GREEN + "레벨 " + state.level,
                List.of(ChatColor.YELLOW + "좌클릭 +1 / 우클릭 -1")));
        inventory.setItem(11, item(Material.SCULK_CATALYST, ChatColor.GREEN + "EXP " + state.exp,
                List.of(ChatColor.YELLOW + "좌클릭 +100 / 우클릭 -100")));
        inventory.setItem(12, item(Material.GOLDEN_APPLE, ChatColor.RED + "완전 회복",
                List.of(ChatColor.YELLOW + "체력·허기·AP·생명 상태 복원")));
        inventory.setItem(13, item(Material.LIGHT_BLUE_DYE, ChatColor.AQUA + "AP " + Math.round(state.ap) + "/" + state.maxAp,
                List.of(ChatColor.YELLOW + "클릭: AP 완충")));
        inventory.setItem(14, item(state.testInvulnerable ? Material.TOTEM_OF_UNDYING : Material.SHIELD,
                ChatColor.YELLOW + "무적 " + state.testInvulnerable, List.of(ChatColor.YELLOW + "클릭하여 전환")));
        inventory.setItem(15, item(Material.IRON_CHESTPLATE,
                ChatColor.BLUE + "피해 감소 " + percent(state.testDamageReductionRate),
                List.of(ChatColor.YELLOW + "좌클릭 +10% / 우클릭 -10%")));
        inventory.setItem(16, item(Material.NETHERITE_SWORD,
                ChatColor.RED + "주는 피해 x" + round(state.testDamageDealtMultiplier),
                List.of(ChatColor.YELLOW + "좌클릭 +0.5 / 우클릭 -0.5")));

        inventory.setItem(19, item(Material.CHEST, ChatColor.GOLD + "모든 자원 50",
                List.of(ChatColor.YELLOW + "클릭하여 공용 원장 채우기")));
        inventory.setItem(20, item(Material.IRON_SWORD, ChatColor.GOLD + "무기: "
                + (state.mainWeaponId == null ? "UNARMED" : state.mainWeaponId),
                List.of(ChatColor.YELLOW + "클릭: 다음 대표 무기")));
        inventory.setItem(21, item(Material.COOKED_BEEF, ChatColor.GREEN + "응급 배급 10",
                List.of(ChatColor.YELLOW + "클릭하여 Q1 설정")));
        inventory.setItem(22, item(Material.MILK_BUCKET, ChatColor.WHITE + "플레이어 상태 정화",
                List.of(ChatColor.YELLOW + "클릭: 모든 효과 해제")));
        inventory.setItem(23, item(Material.FERMENTED_SPIDER_EYE, ChatColor.DARK_PURPLE + "플레이어 둔화",
                List.of(ChatColor.YELLOW + "클릭: 30초 부여")));

        inventory.setItem(28, item(Material.ROTTEN_FLESH, ChatColor.RED + "근접 적 소환",
                List.of(ChatColor.YELLOW + "굶주린 배회자 1기")));
        inventory.setItem(29, item(Material.IRON_BLOCK, ChatColor.RED + "장갑 적 소환",
                List.of(ChatColor.YELLOW + "부패한 강타자 1기")));
        inventory.setItem(30, item(Material.RAVAGER_SPAWN_EGG, ChatColor.DARK_RED + "Day 10 보스",
                List.of(ChatColor.YELLOW + "클릭: 새 보스 소환")));
        inventory.setItem(31, item(Material.RESPAWN_ANCHOR, ChatColor.DARK_PURPLE + "보스 2페이즈",
                List.of(ChatColor.YELLOW + "클릭: 협동 채널 강제 진입")));
        inventory.setItem(32, item(Material.BELL, ChatColor.RED + "보스 패턴 실행",
                List.of(ChatColor.YELLOW + "클릭: 현재 페이즈 패턴")));
        inventory.setItem(33, item(Material.LAVA_BUCKET, ChatColor.RED + "테스트 몬스터 정리",
                List.of(ChatColor.YELLOW + "클릭: 현재 회차 태그 개체 제거")));
        inventory.setItem(34, item(Material.SPYGLASS, ChatColor.AQUA + "조준 대상 검사",
                List.of(ChatColor.YELLOW + "클릭: HP·방어·브레이크·상태 출력")));

        inventory.setItem(37, item(Material.ARMOR_STAND, ChatColor.YELLOW + "빈사 더미 생성",
                List.of(ChatColor.YELLOW + "웅크리고 우클릭하여 솔로 구조 테스트")));
        inventory.setItem(38, item(Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE + "보스 가상 기여",
                List.of(ChatColor.YELLOW + "좌클릭 2명 / 우클릭 1명")));
        inventory.setItem(39, item(Material.DAYLIGHT_DETECTOR, ChatColor.GOLD + "Day " + run.day,
                List.of(ChatColor.YELLOW + "좌클릭 다음 / 우클릭 이전")));
        inventory.setItem(40, item(Material.CLOCK, ChatColor.YELLOW + "시간 배율 x" + round(run.test.timeScale),
                List.of(ChatColor.YELLOW + "클릭: 0.25→1→5→20")));
        inventory.setItem(41, item(Material.REPEATER, ChatColor.YELLOW + "시간 20틱 진행",
                List.of(ChatColor.YELLOW + "정지 중에도 논리 시간 단계 실행")));
        inventory.setItem(42, item(Material.WRITABLE_BOOK, ChatColor.AQUA + "스냅샷 생성",
                List.of(ChatColor.YELLOW + "현재 런타임·인벤토리 저장")));
        inventory.setItem(43, item(Material.ENDER_EYE, ChatColor.AQUA + "한 단계 되돌리기",
                List.of(ChatColor.YELLOW + "최근 변경 전 스냅샷 복원")));
        inventory.setItem(44, item(Material.MAP, ChatColor.AQUA + "JSON 내보내기",
                List.of(ChatColor.YELLOW + "회차·플레이어·조준 대상·틱 지표")));

        inventory.setItem(49, item(Material.BARRIER, ChatColor.RED + "테스트 초기화",
                List.of(ChatColor.YELLOW + "Shift+클릭 필요", ChatColor.GRAY + "입장 전 백업은 유지")));
        inventory.setItem(53, item(Material.OAK_DOOR, ChatColor.RED + "Test Lab 종료",
                List.of(ChatColor.WHITE + "/ws test exit GUI_EXIT --confirm",
                        ChatColor.GRAY + "명령 확인 후 입장 전 상태 복원")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) {
            return;
        }
        if (!player.hasPermission("wildsurvival.test.mutate")) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Missing permission wildsurvival.test.mutate");
            return;
        }
        try {
            handle(player, event.getRawSlot(), event.getClick());
            if (event.getRawSlot() != 4 && event.getRawSlot() != 49 && lab.owner(player)) {
                Bukkit.getScheduler().runTask(plugin, () -> open(player));
            }
        } catch (Exception exception) {
            player.sendMessage(ChatColor.RED + "[Test Lab] " + exception.getMessage());
        }
    }

    private void handle(Player player, int slot, ClickType click) throws Exception {
        RunSnapshot run = runs.current().orElseThrow();
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        boolean backwards = click.isRightClick();
        switch (slot) {
            case 2 -> lab.loadPreset(player, next(lab.listPresets(), run.test.activePreset, false));
            case 4 -> scenarios.run(player, next(TestScenarioService.IDS, run.test.activeScenario, false));
            case 6 -> lab.setVirtualPartySize(player, wrap(run.test.virtualPartySize + (backwards ? -1 : 1), 1, 4));
            case 8 -> lab.setTimeFrozen(player, !run.test.timeFrozen);
            case 10 -> lab.setLevel(player, Math.max(1, Math.min(50, state.level + (backwards ? -1 : 1))));
            case 11 -> lab.setExperience(player, Math.max(0, state.exp + (backwards ? -100 : 100)));
            case 12 -> lab.heal(player);
            case 13 -> lab.setAp(player, state.maxAp);
            case 14 -> lab.setInvulnerable(player, !state.testInvulnerable);
            case 15 -> lab.setPlayerStat(player, "damage-reduction",
                    Math.max(0.0, Math.min(0.95, state.testDamageReductionRate + (backwards ? -0.10 : 0.10))));
            case 16 -> lab.setPlayerStat(player, "damage-dealt",
                    Math.max(0.0, Math.min(100.0, state.testDamageDealtMultiplier + (backwards ? -0.5 : 0.5))));
            case 19 -> lab.fillResources(player, 50);
            case 20 -> {
                List<String> weapons = new ArrayList<>(lab.weaponIds());
                lab.setEquipment(player, next(weapons, state.mainWeaponId == null ? "UNARMED" : state.mainWeaponId, backwards), true);
            }
            case 21 -> lab.setQuickItem(player, "RATION", 10);
            case 22 -> lab.clearPlayerStatuses(player, true);
            case 23 -> lab.addPlayerStatus(player, "slowness", 600, 0);
            case 28 -> lab.spawnEnemy(player, "EN-D1-01", 1);
            case 29 -> lab.spawnEnemy(player, "EN-D4-01", 1);
            case 30 -> lab.spawnBoss(player);
            case 31 -> lab.forceBossPhaseTwo(player);
            case 32 -> lab.forceBossPattern(player);
            case 33 -> player.sendMessage(ChatColor.YELLOW + "테스트 몬스터 " + lab.clearMobs(player) + "기 제거");
            case 34 -> sendTarget(player, lab.inspectTarget(player));
            case 37 -> virtualParty.spawn(player, "ALLY-" + (virtualParty.list().size() + 1), "DOWNED");
            case 38 -> lab.simulateBossContributors(player, backwards ? 1 : 2);
            case 39 -> {
                List<Integer> days = List.of(1, 3, 6, 10);
                int index = days.indexOf(run.day);
                int next = Math.floorMod(index + (backwards ? -1 : 1), days.size());
                lab.setDay(player, days.get(next));
            }
            case 40 -> {
                List<Double> scales = List.of(0.25, 1.0, 5.0, 20.0);
                lab.setTimeScale(player, scales.get(Math.floorMod(indexOfNearest(scales, run.test.timeScale) + 1, scales.size())));
            }
            case 41 -> lab.stepTime(player, 20L);
            case 42 -> player.sendMessage(ChatColor.AQUA + "스냅샷: " + lab.checkpoint(player, "GUI_MANUAL").snapshotId);
            case 43 -> player.sendMessage(ChatColor.AQUA + "복원: " + lab.undo(player).snapshotId);
            case 44 -> {
                Path path = lab.export(player);
                player.sendMessage(ChatColor.AQUA + "내보내기: " + path);
            }
            case 49 -> {
                if (!click.isShiftClick()) {
                    player.sendMessage(ChatColor.YELLOW + "초기화는 Shift+클릭하세요.");
                    return;
                }
                virtualParty.clear();
                lab.reset(player);
            }
            default -> { }
        }
    }

    private static void sendTarget(Player player, CombatService.CombatEntityView view) {
        player.sendMessage(ChatColor.AQUA + "[대상] " + view.enemyId() + " " + view.entityType());
        player.sendMessage(ChatColor.WHITE + "HP " + round(view.health()) + "/" + round(view.maxHealth())
                + " DEF " + round(view.defence()) + " BREAK " + round(view.currentBreak()) + "/" + round(view.maxBreak()));
        player.sendMessage(ChatColor.GRAY + "ATK " + round(view.attackDamage()) + " AI " + view.ai()
                + " 무적 " + view.invulnerable() + " 상태 " + view.statuses());
    }

    private static void fill(Inventory inventory, Material material) {
        ItemStack filler = item(material, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static <T> T next(List<T> values, T current, boolean backwards) {
        int index = values.indexOf(current);
        return values.get(Math.floorMod((index < 0 ? 0 : index) + (backwards ? -1 : 1), values.size()));
    }

    private static int wrap(int value, int minimum, int maximum) {
        return value < minimum ? maximum : value > maximum ? minimum : value;
    }

    private static int indexOfNearest(List<Double> values, double current) {
        int best = 0;
        double distance = Double.MAX_VALUE;
        for (int index = 0; index < values.size(); index++) {
            double candidate = Math.abs(values.get(index) - current);
            if (candidate < distance) {
                best = index;
                distance = candidate;
            }
        }
        return best;
    }

    private static String percent(double value) {
        return Math.round(value * 100.0) + "%";
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class Holder implements InventoryHolder {
        private final java.util.UUID owner;

        private Holder(java.util.UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
