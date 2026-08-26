package com.lsc.corp.wsplugin;

import com.lsc.corp.wsplugin.boss.PrototypeBossService;
import com.lsc.corp.wsplugin.combat.AmmoService;
import com.lsc.corp.wsplugin.combat.CombatService;
import com.lsc.corp.wsplugin.combat.DamageNumberService;
import com.lsc.corp.wsplugin.content.ContentBundleService;
import com.lsc.corp.wsplugin.content.ProductionBundleValidator;
import com.lsc.corp.wsplugin.economy.EconomyService;
import com.lsc.corp.wsplugin.economy.ItemCodexService;
import com.lsc.corp.wsplugin.economy.LootService;
import com.lsc.corp.wsplugin.death.GraveService;
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
import com.lsc.corp.wsplugin.story.StoryService;
import com.lsc.corp.wsplugin.finale.FinalService;
import com.lsc.corp.wsplugin.ui.PlayerMenuService;
import com.lsc.corp.wsplugin.research.ResearchService;
import com.lsc.corp.wsplugin.status.StatusService;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    private RunService runService;
    private TelemetryService telemetry;
    private DamageNumberService damageNumbers;
    private TutorialService tutorial;
    private FinalService finale;
    private StatusService statuses;
    private CombatService combat;

    @Override
    public void onEnable() {
        try {
            ContentBundleService content = new ContentBundleService(getClassLoader());
            content.loadAndValidate();

            telemetry = new TelemetryService(getDataFolder().toPath());
            RunRepository seasonRepository = RunRepository.season1(getDataFolder().toPath());
            RunRepository legacyPrototypeRepository = new RunRepository(getDataFolder().toPath());
            RunRepository testRepository = RunRepository.testLab(getDataFolder().toPath());
            runService = new RunService(this, seasonRepository, legacyPrototypeRepository, testRepository,
                    content.content(), ProductionBundleValidator.REVISION, telemetry);
            tutorial = new TutorialService(this, runService);

            ItemCodexService codex = new ItemCodexService(this, runService, content.content(), content.productionCatalog(), telemetry);
            GrowthService growth = new GrowthService(this, runService, content.content(), content.productionCatalog(), telemetry);
            EquipmentService equipment = new EquipmentService(this, runService, content.content(),
                    content.productionCatalog(), telemetry, codex);
            SkillLoadoutService skills = new SkillLoadoutService(runService, content.content(), content.productionCatalog(), equipment);
            statuses = new StatusService(this, runService, content.productionCatalog(), equipment, telemetry);
            combat = new CombatService(this, runService, content.content(), content.productionCatalog(),
                    equipment, growth, skills, telemetry, statuses);
            GraveService graves = new GraveService(this, runService, content.content(),
                    content.productionCatalog(), codex, equipment, telemetry);
            combat.setDeathHandler(graves::prepareDeath);
            EconomyService economy = new EconomyService(this, runService, content.content(),
                    content.productionCatalog(), equipment, growth, telemetry, codex);
            LootService loot = new LootService(runService, content.productionCatalog(), codex, equipment, growth, telemetry);
            PlayerStatService stats = new PlayerStatService(this, runService, growth, equipment);
            equipment.setStatRefresher(stats::apply);
            FacilityService facility = new FacilityService(this, runService, content.productionCatalog(), codex, equipment, telemetry);
            AmmoService ammo = new AmmoService(runService, content.productionCatalog(), codex, telemetry);
            ResearchService research = new ResearchService(runService, content.productionCatalog());
            facility.setOpeners(economy::openCraft, economy::openLedger, codex::open, stats::open,
                    research::open, growth::openAugments);
            economy.setVirtualFacilityHandler(facility::canAssembleVirtual, facility::assembleVirtual);
            DiscoveryService discoveries = new DiscoveryService(runService, content.productionCatalog());
            StoryService story = new StoryService(runService, content.productionCatalog());
            damageNumbers = new DamageNumberService(this, runService);
            combat.setItemRewardHandler((player, resourceId, amount) -> codex.grantResource(player, resourceId, amount));
            combat.setProductionLootHandler(loot::rewardEnemy);
            combat.setDamageNumbers(damageNumbers);
            combat.setFacilityService(facility);
            combat.setAmmoService(ammo);
            facility.setCombatService(combat);
            PrototypeBossService boss = new PrototypeBossService(this, runService, content.productionCatalog(),
                    combat, growth, loot, telemetry);
            finale = new FinalService(this, runService, content.productionCatalog(), codex, combat, discoveries, story);
            PlayerMenuService menu = new PlayerMenuService(runService, economy, codex, stats, equipment, skills,
                    growth, tutorial, discoveries, story, finale, research);
            combat.setMenuOpener(menu::open);
            combat.setBossDamageHandler((attacker, entity, damage, breakDamage, executionId) -> {
                if (finale.handles(entity)) finale.damage(attacker, entity, damage, breakDamage, executionId);
                else boss.damage(attacker, entity, damage, breakDamage, executionId);
            });
            PrototypeLoopService loop = new PrototypeLoopService(this, runService,
                    content.productionCatalog(), economy, combat, graves, boss, growth, stats, telemetry);

            TestLabRepository testLabRepository = new TestLabRepository(getDataFolder().toPath());
            TestLabService testLab = new TestLabService(this, runService, testLabRepository, content.content(),
                    content.productionCatalog(), facility, equipment, growth, combat, boss, loop, telemetry);
            VirtualPartyService virtualParty = new VirtualPartyService(this, runService);
            TestScenarioService scenarios = new TestScenarioService(testLab, runService, growth, combat, boss, virtualParty, economy);
            TestLabGui testLabGui = new TestLabGui(this, testLab, scenarios, virtualParty, runService);
            TestLabCommand testLabCommand = new TestLabCommand(testLab, testLabGui, scenarios, virtualParty, combat, runService);

            runService.attach(loop, equipment, growth);
            registerListeners(equipment, skills, statuses, combat, graves, economy, facility, ammo, codex, stats, menu, discoveries, story, finale,
                    research, tutorial, damageNumbers, growth, boss, loop, testLab, virtualParty, testLabGui);

            PrototypeCommand command = new PrototypeCommand(content, runService, equipment, economy, growth, boss, telemetry, testLabCommand, menu);
            Objects.requireNonNull(getCommand("wildsurvival"), "wildsurvival command").setExecutor(command);
            Objects.requireNonNull(getCommand("wildsurvival"), "wildsurvival command").setTabCompleter(command);

            runService.restore();
            facility.restore();
            graves.restore();
            research.restore();
            finale.restore();
            getServer().getScheduler().runTaskTimer(this, facility::tick, 20L, 20L);
            getServer().getScheduler().runTaskTimer(this, discoveries::tick, 20L, 20L);
            getServer().getScheduler().runTaskTimer(this, story::tick, 30L, 20L);
            getServer().getScheduler().runTaskTimer(this, research::tick, 30L, 20L);
            getServer().getScheduler().runTaskTimer(this, finale::tick, 40L, 1L);
            getServer().getScheduler().runTaskTimer(this, statuses::tick, 1L, 1L);
            tutorial.start();
            for (org.bukkit.entity.Player player : runService.onlineMembers()) {
                codex.reconcile(player);
                loot.deliverPending(player);
                stats.apply(player);
            }
            virtualParty.cleanupOrphans();
            testLab.recoverActiveSession();
            runService.startHeartbeat();
            getLogger().info("WildSurvival Season 1 runtime + ws-content-r2 catalog are ready (70 files, 334 codex entries, 21 statuses).");
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
        if (finale != null) {
            finale.cleanup();
        }
        if (combat != null) {
            combat.shutdown();
        }
        if (statuses != null) {
            statuses.shutdown();
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
