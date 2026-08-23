package com.lsc.corp.wsplugin;

import com.lsc.corp.wsplugin.boss.PrototypeBossService;
import com.lsc.corp.wsplugin.combat.CombatService;
import com.lsc.corp.wsplugin.combat.DamageNumberService;
import com.lsc.corp.wsplugin.content.ContentBundleService;
import com.lsc.corp.wsplugin.economy.EconomyService;
import com.lsc.corp.wsplugin.economy.ItemCodexService;
import com.lsc.corp.wsplugin.economy.LootService;
import com.lsc.corp.wsplugin.facility.FacilityService;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.ops.PrototypeCommand;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.EquipmentService;
import com.lsc.corp.wsplugin.player.PlayerStatService;
import com.lsc.corp.wsplugin.player.SkillLoadoutService;
import com.lsc.corp.wsplugin.run.RunRepository;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.testlab.TestLabCommand;
import com.lsc.corp.wsplugin.testlab.TestLabGui;
import com.lsc.corp.wsplugin.testlab.TestLabRepository;
import com.lsc.corp.wsplugin.testlab.TestLabService;
import com.lsc.corp.wsplugin.testlab.TestScenarioService;
import com.lsc.corp.wsplugin.testlab.VirtualPartyService;
import com.lsc.corp.wsplugin.tutorial.TutorialService;
import com.lsc.corp.wsplugin.world.PrototypeLoopService;
import com.lsc.corp.wsplugin.world.DiscoveryService;
import com.lsc.corp.wsplugin.ui.PlayerMenuService;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    private RunService runService;
    private TelemetryService telemetry;
    private DamageNumberService damageNumbers;
    private TutorialService tutorial;

    @Override
    public void onEnable() {
        try {
            ContentBundleService content = new ContentBundleService(getClassLoader());
            content.loadAndValidate();

            telemetry = new TelemetryService(getDataFolder().toPath());
            RunRepository repository = new RunRepository(getDataFolder().toPath());
            RunRepository testRepository = RunRepository.testLab(getDataFolder().toPath());
            runService = new RunService(this, repository, testRepository, content.content(), telemetry);
            tutorial = new TutorialService(this, runService);

            ItemCodexService codex = new ItemCodexService(this, runService, content.content(), content.productionCatalog(), telemetry);
            GrowthService growth = new GrowthService(this, runService, content.content(), content.productionCatalog(), telemetry);
            EquipmentService equipment = new EquipmentService(this, runService, content.content(),
                    content.productionCatalog(), telemetry, codex);
            SkillLoadoutService skills = new SkillLoadoutService(runService, content.content(), content.productionCatalog(), equipment);
            CombatService combat = new CombatService(this, runService, content.content(), content.productionCatalog(),
                    equipment, growth, skills, telemetry);
            EconomyService economy = new EconomyService(this, runService, content.content(),
                    content.productionCatalog(), equipment, growth, telemetry, codex);
            LootService loot = new LootService(runService, content.productionCatalog(), codex, equipment, growth, telemetry);
            PlayerStatService stats = new PlayerStatService(this, runService, growth, equipment);
            equipment.setStatRefresher(stats::apply);
            FacilityService facility = new FacilityService(this, runService, content.productionCatalog(), codex, equipment, telemetry);
            facility.setOpeners(economy::openCraft, economy::openLedger, codex::open, stats::open);
            economy.setVirtualFacilityHandler(facility::canAssembleVirtual, facility::assembleVirtual);
            DiscoveryService discoveries = new DiscoveryService(runService, content.productionCatalog());
            PlayerMenuService menu = new PlayerMenuService(runService, economy, codex, stats, equipment, skills,
                    growth, tutorial, discoveries);
            damageNumbers = new DamageNumberService(this, runService);
            combat.setMenuOpener(menu::open);
            combat.setItemRewardHandler((player, resourceId, amount) -> codex.grantResource(player, resourceId, amount));
            combat.setProductionLootHandler(loot::rewardEnemy);
            combat.setDamageNumbers(damageNumbers);
            combat.setFacilityService(facility);
            PrototypeBossService boss = new PrototypeBossService(this, runService, content.productionCatalog(),
                    combat, growth, loot, telemetry);
            combat.setBossDamageHandler(boss);
            PrototypeLoopService loop = new PrototypeLoopService(this, runService,
                    content.productionCatalog(), economy, combat, boss, growth, stats, telemetry);

            TestLabRepository testLabRepository = new TestLabRepository(getDataFolder().toPath());
            TestLabService testLab = new TestLabService(this, runService, testLabRepository, content.content(),
                    content.productionCatalog(), equipment, growth, combat, boss, loop, telemetry);
            VirtualPartyService virtualParty = new VirtualPartyService(this, runService);
            TestScenarioService scenarios = new TestScenarioService(testLab, runService, growth, combat, boss, virtualParty, economy);
            TestLabGui testLabGui = new TestLabGui(this, testLab, scenarios, virtualParty, runService);
            TestLabCommand testLabCommand = new TestLabCommand(testLab, testLabGui, scenarios, virtualParty, combat, runService);

            runService.attach(loop, equipment, growth);
            registerListeners(equipment, skills, combat, economy, facility, codex, stats, menu, discoveries,
                    tutorial, damageNumbers, growth, boss, loop, testLab, virtualParty, testLabGui);

            PrototypeCommand command = new PrototypeCommand(content, runService, equipment, economy, growth, boss, telemetry, testLabCommand, menu);
            Objects.requireNonNull(getCommand("wildsurvival"), "wildsurvival command").setExecutor(command);
            Objects.requireNonNull(getCommand("wildsurvival"), "wildsurvival command").setTabCompleter(command);

            runService.restore();
            facility.restore();
            getServer().getScheduler().runTaskTimer(this, facility::tick, 20L, 20L);
            getServer().getScheduler().runTaskTimer(this, discoveries::tick, 20L, 20L);
            tutorial.start();
            for (org.bukkit.entity.Player player : runService.onlineMembers()) {
                codex.reconcile(player);
                loot.deliverPending(player);
                stats.apply(player);
            }
            virtualParty.cleanupOrphans();
            testLab.recoverActiveSession();
            runService.startHeartbeat();
            getLogger().info("WildSurvival Season 1 runtime + ws-content-r2 catalog are ready (68 files, 334 codex entries).");
        } catch (Exception exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "Prototype bootstrap failed; disabling plugin.", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (tutorial != null) {
            tutorial.shutdown();
        }
        if (damageNumbers != null) {
            damageNumbers.cleanup();
        }
        if (runService != null) {
            runService.shutdown();
        }
        if (telemetry != null) {
            telemetry.close();
        }
    }

    private void registerListeners(org.bukkit.event.Listener... listeners) {
        for (org.bukkit.event.Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }
}
