package com.lsc.corp.wsplugin.testlab;

import com.lsc.corp.wsplugin.boss.PrototypeBossService;
import com.lsc.corp.wsplugin.combat.CombatService;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.run.RunService;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class TestScenarioService {
    public static final List<String> IDS = List.of(
            "SANDBOX", "GATHER", "CRAFT", "COMBAT", "STATUS-BREAK", "AUGMENT",
            "BOSS-PHASE-1", "BOSS-PHASE-2", "DOWNED-REVIVE");
    private final TestLabService lab;
    private final RunService runs;
    private final GrowthService growth;
    private final CombatService combat;
    private final PrototypeBossService boss;
    private final VirtualPartyService virtualParty;

    public TestScenarioService(TestLabService lab, RunService runs, GrowthService growth,
                               CombatService combat, PrototypeBossService boss,
                               VirtualPartyService virtualParty) {
        this.lab = lab;
        this.runs = runs;
        this.growth = growth;
        this.combat = combat;
        this.boss = boss;
        this.virtualParty = virtualParty;
    }

    public String run(Player player, String rawId) throws IOException {
        String id = rawId.toUpperCase(Locale.ROOT).replace('_', '-');
        if (!IDS.contains(id)) {
            throw new IllegalArgumentException("Unknown scenario " + rawId + ". Expected " + IDS);
        }
        lab.checkpoint(player, "BEFORE_SCENARIO_" + id.replace('-', '_'));
        virtualParty.clear();
        lab.reset(player);
        switch (id) {
            case "SANDBOX" -> { }
            case "GATHER" -> {
                lab.setEquipment(player, "PICKAXE", true);
                lab.setDay(player, 1);
                player.sendMessage(ChatColor.YELLOW + "자연 목재·석재·철 블록을 채집해 원장 증가와 곡괭이 전투 우선 판정을 확인하세요.");
            }
            case "CRAFT" -> {
                lab.fillResources(player, 50);
                player.sendMessage(ChatColor.YELLOW + "재료가 각 50개 지급됐습니다. /ws craft로 비용·제작·장착을 확인하세요.");
            }
            case "COMBAT" -> {
                lab.setEquipment(player, "SWORD", true);
                lab.setPlayerStat(player, "ap-regen", 5.0);
                lab.spawnEnemy(player, "EN-D1-01", 2);
                lab.spawnEnemy(player, "EN-D2-01", 1);
                player.sendMessage(ChatColor.YELLOW + "검 기본 공격·W1~W3·회피·원거리 압박 시나리오입니다.");
            }
            case "STATUS-BREAK" -> {
                lab.setEquipment(player, "PICKAXE", true);
                LivingEntity target = lab.spawnEnemy(player, "EN-D4-01", 1);
                combat.setCombatEntityNumber(target, "break", 600.0);
                combat.applyTestStatus(target, "SLOW", 600, 0);
                player.sendMessage(ChatColor.YELLOW + "장갑 적에게 브레이크 75%와 둔화가 적용됐습니다.");
            }
            case "AUGMENT" -> {
                lab.setLevel(player, 3);
                growth.resetPersonalDrawForTest(player, 3);
                player.sendMessage(ChatColor.YELLOW + "레벨 3 실버 개인 증강 3택을 반복 검증합니다.");
            }
            case "BOSS-PHASE-1" -> {
                lab.loadPreset(player, "PARTY-4");
                lab.spawnBoss(player);
                player.sendMessage(ChatColor.YELLOW + "4인 스케일 Day 10 보스 1페이즈입니다.");
            }
            case "BOSS-PHASE-2" -> {
                lab.loadPreset(player, "PARTY-4");
                lab.spawnBoss(player);
                boss.forcePhaseTwoForTest();
                boss.simulateCooperationForTest(1);
                player.sendMessage(ChatColor.YELLOW + "보스 2페이즈와 가상 협동 기여 1/2 상태입니다.");
            }
            case "DOWNED-REVIVE" -> {
                lab.setVirtualPartySize(player, 2);
                virtualParty.spawn(player, "ALLY-1", "DOWNED");
                player.sendMessage(ChatColor.YELLOW + "웅크리고 가상 파티원을 우클릭해 구조 채널을 검증하세요.");
            }
            default -> throw new IllegalStateException("Unhandled scenario " + id);
        }
        runs.mutate(run -> run.test.activeScenario = id);
        return id;
    }

    public void forceBossPattern(Player player) throws IOException {
        lab.forceBossPattern(player);
    }
}
