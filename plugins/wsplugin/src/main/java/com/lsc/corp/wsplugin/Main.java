package com.lsc.corp.wsplugin;

import com.lsc.corp.wsplugin.boss.PrototypeBossService;
import com.lsc.corp.wsplugin.combat.CombatService;
import com.lsc.corp.wsplugin.content.ContentBundleService;
import com.lsc.corp.wsplugin.economy.EconomyService;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.ops.PrototypeCommand;
import com.lsc.corp.wsplugin.ops.TelemetryService;
import com.lsc.corp.wsplugin.player.EquipmentService;
import com.lsc.corp.wsplugin.run.RunRepository;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.testlab.TestLabCommand;
import com.lsc.corp.wsplugin.testlab.TestLabGui;
import com.lsc.corp.wsplugin.testlab.TestLabRepository;
import com.lsc.corp.wsplugin.testlab.TestLabService;
import com.lsc.corp.wsplugin.testlab.TestScenarioService;
import com.lsc.corp.wsplugin.testlab.VirtualPartyService;
import com.lsc.corp.wsplugin.world.PrototypeLoopService;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    private RunService runService;
    private TelemetryService telemetry;

    @Override
    public void onEnable() {
        try {
            ContentBundleService content = new ContentBundleService(getClassLoader());
            content.loadAndValidate();

            telemetry = new TelemetryService(getDataFolder().toPath());
            RunRepository repository = new RunRepository(getDataFolder().toPath());
            RunRepository testRepository = RunRepository.testLab(getDataFolder().toPath());
            runService = new RunService(this, repository, testRepository, content.content(), telemetry);

            GrowthService growth = new GrowthService(this, runService, content.content(), telemetry);
            EquipmentService equipment = new EquipmentService(this, runService, content.content(), telemetry);
            CombatService combat = new CombatService(this, runService, content.content(), equipment, growth, telemetry);
            EconomyService economy = new EconomyService(this, runService, content.content(), equipment, growth, telemetry);
            PrototypeBossService boss = new PrototypeBossService(this, runService, content.content(), combat, growth, telemetry);
            combat.setBossDamageHandler(boss);
            PrototypeLoopService loop = new PrototypeLoopService(this, runService, content.content(), economy, combat, boss, growth, telemetry);

            TestLabRepository testLabRepository = new TestLabRepository(getDataFolder().toPath());
            TestLabService testLab = new TestLabService(this, runService, testLabRepository, content.content(),
                    equipment, growth, combat, boss, loop, telemetry);
            VirtualPartyService virtualParty = new VirtualPartyService(this, runService);
            TestScenarioService scenarios = new TestScenarioService(testLab, runService, growth, combat, boss, virtualParty);
            TestLabGui testLabGui = new TestLabGui(this, testLab, scenarios, virtualParty, runService);
            TestLabCommand testLabCommand = new TestLabCommand(testLab, testLabGui, scenarios, virtualParty, combat, runService);

            runService.attach(loop, equipment, growth);
            registerListeners(equipment, combat, economy, growth, boss, loop, testLab, virtualParty, testLabGui);

            PrototypeCommand command = new PrototypeCommand(content, runService, equipment, economy, growth, boss, telemetry, testLabCommand);
            Objects.requireNonNull(getCommand("wildsurvival"), "wildsurvival command").setExecutor(command);
            Objects.requireNonNull(getCommand("wildsurvival"), "wildsurvival command").setTabCompleter(command);

            runService.restore();
            virtualParty.cleanupOrphans();
            testLab.recoverActiveSession();
            runService.startHeartbeat();
            getLogger().info("WildSurvival ws-prototype-r1 is ready.");
        } catch (Exception exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "Prototype bootstrap failed; disabling plugin.", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
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
