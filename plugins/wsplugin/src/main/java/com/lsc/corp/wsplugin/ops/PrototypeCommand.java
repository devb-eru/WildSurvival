package com.lsc.corp.wsplugin.ops;

import com.lsc.corp.wsplugin.boss.PrototypeBossService;
import com.lsc.corp.wsplugin.content.ContentBundleService;
import com.lsc.corp.wsplugin.content.ProductionBundleValidator;
import com.lsc.corp.wsplugin.economy.EconomyService;
import com.lsc.corp.wsplugin.growth.GrowthService;
import com.lsc.corp.wsplugin.player.EquipmentService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import com.lsc.corp.wsplugin.testlab.TestLabCommand;
import com.lsc.corp.wsplugin.ui.PlayerMenuService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class PrototypeCommand implements CommandExecutor, TabCompleter {
    private final ContentBundleService content;
    private final RunService runs;
    private final EquipmentService equipment;
    private final EconomyService economy;
    private final GrowthService growth;
    private final PrototypeBossService boss;
    private final TelemetryService telemetry;
    private final TestLabCommand testLab;
    private final PlayerMenuService menu;

    public PrototypeCommand(ContentBundleService content, RunService runs, EquipmentService equipment,
                            EconomyService economy, GrowthService growth, PrototypeBossService boss,
                            TelemetryService telemetry, TestLabCommand testLab, PlayerMenuService menu) {
        this.content = content;
        this.runs = runs;
        this.equipment = equipment;
        this.economy = economy;
        this.growth = growth;
        this.boss = boss;
        this.telemetry = telemetry;
        this.testLab = testLab;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 0) {
                help(sender, label);
                return true;
            }
            String root = args[0].toLowerCase(Locale.ROOT);
            switch (root) {
                case "content" -> content(sender, args);
                case "prototype" -> prototype(sender, args);
                case "equipment" -> equipment.open(requirePlayer(sender));
                case "menu" -> menu.open(requirePlayer(sender));
                case "craft" -> economy.openCraft(requirePlayer(sender));
                case "codex" -> menu.openCodex(requirePlayer(sender));
                case "stats" -> menu.openStats(requirePlayer(sender));
                case "settings" -> menu.openSettings(requirePlayer(sender));
                case "skills" -> menu.openSkills(requirePlayer(sender));
                case "guide" -> menu.openGuide(requirePlayer(sender));
                case "ledger" -> economy.openLedger(requirePlayer(sender));
                case "status" -> status(sender);
                case "augment" -> growth.openPendingPersonalDraw(requirePlayer(sender));
                case "test" -> testLab.execute(sender, args);
                default -> help(sender, label);
            }
        } catch (Exception exception) {
            sender.sendMessage(ChatColor.RED + "WildSurvival: " + exception.getMessage());
        }
        return true;
    }

    private void content(CommandSender sender, String[] args) throws Exception {
        requirePermission(sender, "wildsurvival.content.validate");
        if (args.length < 2 || !"validate".equalsIgnoreCase(args[1])) {
            throw new IllegalArgumentException("/ws content validate");
        }
        var result = content.loadAndValidate();
        var production = content.productionValidation();
        sender.sendMessage(ChatColor.GREEN + result.manifest().contentRevision() + " L0 validated: "
                + result.verifiedFileCount() + " files, promotionForbidden=" + result.manifest().promotionForbidden());
        sender.sendMessage(ChatColor.GREEN + ProductionBundleValidator.REVISION + " production validated: "
                + production.verifiedFileCount() + " files, codex=" + production.catalog().codexEntries().size()
                + ", recipes=" + production.catalog().recipes().size() + ", skills=" + production.catalog().skills().size());
    }

    private void prototype(CommandSender sender, String[] args) throws Exception {
        requirePermission(sender, "wildsurvival.prototype.admin");
        if (args.length < 2) {
            throw new IllegalArgumentException("/ws prototype <create|start|stop|inspect|advance|boss>");
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                Collection<Player> players = resolvePlayers(args);
                RunSnapshot snapshot = runs.create(players);
                telemetry.audit(snapshot.runId, sender.getName(), "prototype.create", "members=" + players.size());
                sender.sendMessage(ChatColor.GREEN + "Created " + snapshot.runId + " with " + players.stream().map(Player::getName).toList());
            }
            case "start" -> {
                runs.start();
                audit(sender, "prototype.start", "confirmed");
            }
            case "stop" -> {
                if (java.util.Arrays.asList(args).contains("--dry-run")) {
                    sender.sendMessage(ChatColor.YELLOW + "DRY-RUN: would stop " + runs.inspect());
                    return;
                }
                if (!java.util.Arrays.asList(args).contains("--confirm")) {
                    throw new IllegalArgumentException("중단은 /ws prototype stop <reason> --confirm 또는 --dry-run을 사용하세요.");
                }
                String reason = args.length >= 3 ? args[2] : "ADMIN_STOP";
                runs.stop(reason, sender.getName());
            }
            case "inspect" -> sender.sendMessage(ChatColor.AQUA + runs.inspect());
            case "advance" -> {
                runs.forceAdvance();
                audit(sender, "prototype.advance", "manual checkpoint");
            }
            case "boss" -> {
                boss.spawn();
                audit(sender, "prototype.boss", "manual spawn");
            }
            default -> throw new IllegalArgumentException("Unknown prototype subcommand " + sub);
        }
    }

    private void status(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + runs.inspect());
        if (sender instanceof Player player) {
            runs.playerState(player.getUniqueId()).ifPresent(state -> sender.sendMessage(ChatColor.WHITE
                    + "life=" + state.lifeState + " level=" + state.level + " exp=" + state.exp
                    + " ap=" + Math.round(state.ap) + "/" + state.maxAp + " weapon="
                    + (state.mainWeaponId == null ? "UNARMED" : state.mainWeaponId)
                    + " trident=" + state.tridentState + " augments=" + state.personalAugments));
        }
        sender.sendMessage(ChatColor.GRAY + "pluginTickP95=" + Math.round(telemetry.p95PluginTickMs() * 100.0) / 100.0 + "ms");
    }

    private Collection<Player> resolvePlayers(String[] args) {
        Map<java.util.UUID, Player> players = new LinkedHashMap<>();
        if (args.length <= 2) {
            Bukkit.getOnlinePlayers().forEach(player -> players.put(player.getUniqueId(), player));
        } else {
            for (int i = 2; i < args.length; i++) {
                Player player = Bukkit.getPlayerExact(args[i]);
                if (player == null) {
                    throw new IllegalArgumentException("Offline or unknown player: " + args[i]);
                }
                players.put(player.getUniqueId(), player);
            }
        }
        return players.values();
    }

    private void audit(CommandSender sender, String action, String reason) {
        RunSnapshot snapshot = runs.current().orElseThrow();
        telemetry.audit(snapshot.runId, sender.getName(), action, reason);
    }

    private static Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("Player-only command");
        }
        return player;
    }

    private static void requirePermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            throw new SecurityException("Missing permission " + permission);
        }
    }

    private static void help(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "WildSurvival prototype");
        sender.sendMessage(ChatColor.WHITE + "/" + label + " menu | guide | equipment | skills | craft | ledger | status | augment | test");
        if (sender.hasPermission("wildsurvival.prototype.admin")) {
            sender.sendMessage(ChatColor.GRAY + "/" + label + " prototype create [players...] | start | inspect | advance | boss");
            sender.sendMessage(ChatColor.GRAY + "/" + label + " prototype stop <reason> --dry-run|--confirm");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("prototype", "content", "menu", "guide", "equipment", "skills", "craft", "codex", "stats", "settings", "ledger", "status", "augment", "test"));
        }
        if (args.length >= 2 && "test".equalsIgnoreCase(args[0])) {
            return testLab.complete(sender, args);
        }
        if (args.length == 2 && "prototype".equalsIgnoreCase(args[0])) {
            return filter(args[1], List.of("create", "start", "stop", "inspect", "advance", "boss"));
        }
        if (args.length >= 3 && "prototype".equalsIgnoreCase(args[0]) && "create".equalsIgnoreCase(args[1])) {
            return filter(args[args.length - 1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 2 && "content".equalsIgnoreCase(args[0])) {
            return filter(args[1], List.of("validate"));
        }
        return List.of();
    }

    private static List<String> filter(String prefix, List<String> candidates) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
