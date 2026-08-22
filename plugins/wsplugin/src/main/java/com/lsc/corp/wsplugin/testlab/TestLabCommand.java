package com.lsc.corp.wsplugin.testlab;

import com.lsc.corp.wsplugin.combat.CombatService;
import com.lsc.corp.wsplugin.run.RunService;
import com.lsc.corp.wsplugin.run.RunSnapshot;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TestLabCommand {
    private final TestLabService lab;
    private final TestLabGui gui;
    private final TestScenarioService scenarios;
    private final VirtualPartyService virtualParty;
    private final CombatService combat;
    private final RunService runs;

    public TestLabCommand(TestLabService lab, TestLabGui gui, TestScenarioService scenarios,
                          VirtualPartyService virtualParty, CombatService combat, RunService runs) {
        this.lab = lab;
        this.gui = gui;
        this.scenarios = scenarios;
        this.virtualParty = virtualParty;
        this.combat = combat;
        this.runs = runs;
    }

    public void execute(CommandSender sender, String[] args) throws Exception {
        requirePermission(sender, "wildsurvival.test.use");
        if (args.length < 2) {
            help(sender);
            return;
        }
        String sub = lower(args[1]);
        switch (sub) {
            case "enter" -> enter(sender, args);
            case "exit" -> exit(sender, args);
            case "gui" -> {
                requirePermission(sender, "wildsurvival.test.mutate");
                gui.open(requirePlayer(sender));
            }
            case "status" -> status(sender);
            case "snapshot" -> snapshot(sender, args);
            case "undo" -> {
                requirePermission(sender, "wildsurvival.test.mutate");
                lab.undo(requirePlayer(sender));
            }
            case "reset" -> reset(sender, args);
            case "preset" -> preset(sender, args);
            case "scenario" -> scenario(sender, args);
            case "player" -> player(sender, args);
            case "item" -> item(sender, args);
            case "augment" -> augment(sender, args);
            case "mob" -> mob(sender, args);
            case "world" -> world(sender, args);
            case "party" -> party(sender, args);
            case "damage" -> damage(sender, args);
            case "inspect" -> inspect(sender, args);
            case "export" -> {
                requirePermission(sender, "wildsurvival.test.inspect");
                sender.sendMessage(ChatColor.AQUA + "Exported: " + lab.export(requirePlayer(sender)));
            }
            default -> help(sender);
        }
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("wildsurvival.test.use")) {
            return List.of();
        }
        if (args.length == 2) {
            return filter(args[1], List.of("enter", "exit", "gui", "status", "snapshot", "undo", "reset",
                    "preset", "scenario", "player", "item", "augment", "mob", "world", "party",
                    "damage", "inspect", "export"));
        }
        if (args.length == 3) {
            return switch (lower(args[1])) {
                case "preset" -> filter(args[2], List.of("list", "save", "load", "delete"));
                case "scenario" -> filter(args[2], List.of("list", "run"));
                case "player" -> filter(args[2], List.of("level", "exp", "health", "ap", "life", "stat", "invulnerable", "effect"));
                case "item" -> filter(args[2], List.of("resource", "equipment", "quick", "vanilla", "test-clear"));
                case "augment" -> filter(args[2], List.of("personal", "party", "redraw"));
                case "mob" -> filter(args[2], List.of("spawn", "boss", "clear", "remove", "inspect", "set", "flag", "status", "phase", "pattern"));
                case "world" -> filter(args[2], List.of("day", "time", "weather"));
                case "party" -> filter(args[2], List.of("size", "contribute", "boss-channel", "dummy"));
                case "inspect" -> filter(args[2], List.of("run", "player", "target", "dummies"));
                default -> List.of();
            };
        }
        if (args.length == 4) {
            return switch (lower(args[1]) + ":" + lower(args[2])) {
                case "scenario:run" -> filter(args[3], TestScenarioService.IDS);
                case "preset:load", "preset:delete" -> safePresets(args[3]);
                case "item:resource" -> filter(args[3], List.of("set", "add", "fill", "clear"));
                case "item:equipment" -> filter(args[3], List.of("give", "equip", "remove", "clear"));
                case "item:quick" -> filter(args[3], List.of("set"));
                case "item:vanilla" -> filter(args[3], List.of("give"));
                case "augment:personal" -> filter(args[3], List.of("give", "remove", "clear"));
                case "augment:party" -> filter(args[3], List.of("set", "clear"));
                case "mob:spawn" -> filter(args[3], lab.enemyIds());
                case "mob:set" -> filter(args[3], List.of("health", "max-health", "defence", "break", "break-max", "attack"));
                case "mob:flag" -> filter(args[3], List.of("ai", "invulnerable", "glowing"));
                case "mob:status" -> filter(args[3], List.of("add", "clear"));
                case "world:day" -> filter(args[3], List.of("set", "advance"));
                case "world:time" -> filter(args[3], List.of("freeze", "resume", "scale", "step"));
                case "world:weather" -> filter(args[3], List.of("clear", "rain", "thunder"));
                case "party:dummy" -> filter(args[3], List.of("spawn", "list", "clear"));
                case "player:level", "player:exp" -> filter(args[3], List.of("set", "add"));
                case "player:health" -> filter(args[3], List.of("set", "heal"));
                case "player:ap" -> filter(args[3], List.of("set", "full"));
                case "player:stat" -> filter(args[3], List.of("set", "reset"));
                case "player:effect" -> filter(args[3], List.of("add", "clear"));
                default -> List.of();
            };
        }
        if (args.length == 5) {
            return switch (lower(args[1]) + ":" + lower(args[2]) + ":" + lower(args[3])) {
                case "item:resource:set", "item:resource:add" -> filter(args[4], lab.resourceIds());
                case "item:equipment:give", "item:equipment:equip", "item:equipment:remove" -> filter(args[4], lab.weaponIds());
                case "augment:personal:give", "augment:personal:remove" -> filter(args[4], lab.personalAugmentIds());
                case "augment:party:set" -> filter(args[4], lab.partyAugmentIds());
                case "player:stat:set", "player:stat:reset" -> filter(args[4], TestValuePolicy.PLAYER_STATS.keySet());
                case "player:life:set" -> filter(args[4], List.of("ACTIVE", "DOWNED", "DEAD"));
                case "player:invulnerable:set", "mob:flag:ai", "mob:flag:invulnerable", "mob:flag:glowing" -> filter(args[4], List.of("true", "false"));
                default -> List.of();
            };
        }
        return List.of();
    }

    private void enter(CommandSender sender, String[] args) throws IOException {
        requirePermission(sender, "wildsurvival.test.session");
        Player player = requirePlayer(sender);
        long seed = args.length >= 3 ? parseLong(args[2], "seed") : 0L;
        int partySize = args.length >= 4 ? parseInt(args[3], "virtual party size") : 1;
        RunSnapshot run = lab.enter(player, seed, partySize);
        sender.sendMessage(ChatColor.GREEN + "Test Lab run " + run.runId + " seed=" + run.test.deterministicSeed);
    }

    private void exit(CommandSender sender, String[] args) throws IOException {
        requirePermission(sender, "wildsurvival.test.session");
        if (!contains(args, "--confirm")) {
            throw new IllegalArgumentException("/ws test exit <reason> --confirm");
        }
        String reason = args.length >= 3 && !args[2].startsWith("--") ? args[2] : "TEST_COMPLETE";
        virtualParty.clear();
        lab.exit(requirePlayer(sender), reason);
    }

    private void status(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + lab.summary());
        if (sender instanceof Player player && lab.owner(player)) {
            sendMap(sender, lab.playerView(player));
        }
    }

    private void snapshot(CommandSender sender, String[] args) throws IOException {
        requirePermission(sender, "wildsurvival.test.mutate");
        String reason = args.length >= 3 ? args[2] : "MANUAL";
        sender.sendMessage(ChatColor.AQUA + "Snapshot " + lab.checkpoint(requirePlayer(sender), reason).snapshotId);
    }

    private void reset(CommandSender sender, String[] args) throws IOException {
        requirePermission(sender, "wildsurvival.test.mutate");
        if (!contains(args, "--confirm")) {
            throw new IllegalArgumentException("/ws test reset --confirm");
        }
        virtualParty.clear();
        lab.reset(requirePlayer(sender));
    }

    private void preset(CommandSender sender, String[] args) throws IOException {
        requirePermission(sender, "wildsurvival.test.mutate");
        if (args.length < 3) {
            throw new IllegalArgumentException("/ws test preset <list|save|load|delete>");
        }
        Player player = requirePlayer(sender);
        switch (lower(args[2])) {
            case "list" -> sender.sendMessage(ChatColor.AQUA + "Presets: " + lab.listPresets());
            case "save" -> {
                requireArgs(args, 4, "/ws test preset save <id>");
                sender.sendMessage(ChatColor.GREEN + "Saved " + lab.capturePreset(player, args[3]).id);
            }
            case "load" -> {
                requireArgs(args, 4, "/ws test preset load <id>");
                sender.sendMessage(ChatColor.GREEN + "Loaded " + lab.loadPreset(player, args[3]).id);
            }
            case "delete" -> {
                requireArgs(args, 4, "/ws test preset delete <id> --confirm");
                if (!contains(args, "--confirm")) {
                    throw new IllegalArgumentException("Preset deletion requires --confirm");
                }
                lab.deletePreset(player, args[3]);
            }
            default -> throw new IllegalArgumentException("Unknown preset operation " + args[2]);
        }
    }

    private void scenario(CommandSender sender, String[] args) throws IOException {
        requirePermission(sender, "wildsurvival.test.mutate");
        requireArgs(args, 3, "/ws test scenario <list|run>");
        if ("list".equalsIgnoreCase(args[2])) {
            sender.sendMessage(ChatColor.AQUA + "Scenarios: " + TestScenarioService.IDS);
            return;
        }
        requireArgs(args, 4, "/ws test scenario run <id>");
        sender.sendMessage(ChatColor.GREEN + "Scenario " + scenarios.run(requirePlayer(sender), args[3]) + " ready");
    }

    private void player(CommandSender sender, String[] args) throws IOException {
        requirePermission(sender, "wildsurvival.test.mutate");
        requireArgs(args, 3, "/ws test player <level|exp|health|ap|life|stat|invulnerable|effect>");
        Player player = requirePlayer(sender);
        RunSnapshot.PlayerState state = runs.playerState(player.getUniqueId()).orElseThrow();
        switch (lower(args[2])) {
            case "level" -> {
                requireArgs(args, 5, "/ws test player level <set|add> <value>");
                int value = parseInt(args[4], "level");
                lab.setLevel(player, "add".equalsIgnoreCase(args[3]) ? state.level + value : value);
            }
            case "exp" -> {
                requireArgs(args, 5, "/ws test player exp <set|add> <value>");
                int value = parseInt(args[4], "experience");
                lab.setExperience(player, "add".equalsIgnoreCase(args[3]) ? state.exp + value : value);
            }
            case "health" -> {
                requireArgs(args, 4, "/ws test player health <set|heal> [value]");
                if ("heal".equalsIgnoreCase(args[3])) {
                    lab.heal(player);
                } else {
                    requireArgs(args, 5, "/ws test player health set <value>");
                    lab.setHealth(player, parseDouble(args[4], "health"));
                }
            }
            case "ap" -> {
                requireArgs(args, 4, "/ws test player ap <set|full> [value]");
                lab.setAp(player, "full".equalsIgnoreCase(args[3]) ? state.maxAp : parseDouble(required(args, 4), "ap"));
            }
            case "life" -> {
                requireArgs(args, 5, "/ws test player life set <ACTIVE|DOWNED|DEAD>");
                lab.setLifeState(player, args[4]);
            }
            case "stat" -> {
                requireArgs(args, 5, "/ws test player stat <set|reset> <id> [value]");
                String id = args[4];
                double value = "reset".equalsIgnoreCase(args[3]) ? defaultStat(id)
                        : parseDouble(required(args, 5), id);
                lab.setPlayerStat(player, id, value);
            }
            case "invulnerable" -> {
                requireArgs(args, 5, "/ws test player invulnerable set <true|false>");
                lab.setInvulnerable(player, parseBoolean(args[4]));
            }
            case "effect" -> {
                requireArgs(args, 4, "/ws test player effect <add|clear>");
                if ("clear".equalsIgnoreCase(args[3])) {
                    lab.clearPlayerStatuses(player, true);
                } else {
                    requireArgs(args, 6, "/ws test player effect add <type> <durationTicks> [amplifier]");
                    lab.addPlayerStatus(player, args[4], parseInt(args[5], "durationTicks"),
                            args.length >= 7 ? parseInt(args[6], "amplifier") : 0);
                }
            }
            default -> throw new IllegalArgumentException("Unknown player control " + args[2]);
        }
    }

    private void item(CommandSender sender, String[] args) throws IOException {
        requirePermission(sender, "wildsurvival.test.mutate");
        requireArgs(args, 3, "/ws test item <resource|equipment|quick|vanilla|test-clear>");
        Player player = requirePlayer(sender);
        switch (lower(args[2])) {
            case "resource" -> resource(sender, player, args);
            case "equipment" -> equipment(player, args);
            case "quick" -> {
                requireArgs(args, 6, "/ws test item quick set <id> <amount>");
                lab.setQuickItem(player, args[4], parseInt(args[5], "amount"));
            }
            case "vanilla" -> {
                requireArgs(args, 6, "/ws test item vanilla give <material> <amount>");
                lab.giveVanillaItem(player, args[4], parseInt(args[5], "amount"));
            }
            case "test-clear" -> sender.sendMessage(ChatColor.YELLOW + "Removed " + lab.clearTestItems(player) + " test items");
            default -> throw new IllegalArgumentException("Unknown item control " + args[2]);
        }
    }

    private void resource(CommandSender sender, Player player, String[] args) throws IOException {
        requireArgs(args, 4, "/ws test item resource <set|add|fill|clear>");
        switch (lower(args[3])) {
            case "set" -> {
                requireArgs(args, 6, "/ws test item resource set <id> <amount>");
                lab.setResource(player, args[4], parseInt(args[5], "amount"));
            }
            case "add" -> {
                requireArgs(args, 6, "/ws test item resource add <id> <amount>");
                String id = args[4].toUpperCase(Locale.ROOT);
                int current = runs.current().orElseThrow().resources.getOrDefault(id, 0);
                lab.setResource(player, id, Math.max(0, current + parseInt(args[5], "amount")));
            }
            case "fill" -> {
                requireArgs(args, 5, "/ws test item resource fill <amount>");
                lab.fillResources(player, parseInt(args[4], "amount"));
            }
            case "clear" -> lab.fillResources(player, 0);
            default -> throw new IllegalArgumentException("Unknown resource operation " + args[3]);
        }
        sender.sendMessage(ChatColor.AQUA + "Resources " + runs.current().orElseThrow().resources);
    }

    private void equipment(Player player, String[] args) throws IOException {
        requireArgs(args, 4, "/ws test item equipment <give|equip|remove|clear>");
        switch (lower(args[3])) {
            case "give" -> {
                requireArgs(args, 5, "/ws test item equipment give <weaponId>");
                lab.setEquipment(player, args[4], false);
            }
            case "equip" -> {
                requireArgs(args, 5, "/ws test item equipment equip <weaponId|UNARMED>");
                lab.setEquipment(player, args[4], true);
            }
            case "remove" -> {
                requireArgs(args, 5, "/ws test item equipment remove <weaponId>");
                lab.removeEquipment(player, args[4]);
            }
            case "clear" -> lab.clearLoadout(player);
            default -> throw new IllegalArgumentException("Unknown equipment operation " + args[3]);
        }
    }

    private void augment(CommandSender sender, String[] args) throws IOException {
        requirePermission(sender, "wildsurvival.test.mutate");
        requireArgs(args, 4, "/ws test augment <personal|party|redraw> ...");
        Player player = requirePlayer(sender);
        switch (lower(args[2])) {
            case "personal" -> {
                switch (lower(args[3])) {
                    case "give" -> {
                        requireArgs(args, 5, "/ws test augment personal give <id>");
                        lab.setPersonalAugment(player, args[4], true);
                    }
                    case "remove" -> {
                        requireArgs(args, 5, "/ws test augment personal remove <id>");
                        lab.setPersonalAugment(player, args[4], false);
                    }
                    case "clear" -> lab.clearPersonalAugments(player);
                    default -> throw new IllegalArgumentException("Unknown personal augment operation " + args[3]);
                }
            }
            case "party" -> {
                if ("clear".equalsIgnoreCase(args[3])) {
                    lab.clearPartyAugment(player);
                } else {
                    requireArgs(args, 5, "/ws test augment party set <id>");
                    lab.setPartyAugment(player, args[4]);
                }
            }
            case "redraw" -> lab.redrawPersonalAugment(player, parseInt(args[3], "milestone"));
            default -> throw new IllegalArgumentException("Unknown augment scope " + args[2]);
        }
    }

    private void mob(CommandSender sender, String[] args) throws IOException {
        requirePermission(sender, "wildsurvival.test.mutate");
        requireArgs(args, 3, "/ws test mob <spawn|boss|clear|remove|inspect|set|flag|status|phase|pattern>");
        Player player = requirePlayer(sender);
        switch (lower(args[2])) {
            case "spawn" -> {
                requireArgs(args, 4, "/ws test mob spawn <enemyId> [count]");
                lab.spawnEnemy(player, args[3], args.length >= 5 ? parseInt(args[4], "count") : 1);
            }
            case "boss" -> lab.spawnBoss(player);
            case "clear" -> sender.sendMessage(ChatColor.YELLOW + "Removed " + lab.clearMobs(player) + " mobs");
            case "remove" -> lab.removeTarget(player);
            case "inspect" -> sendObject(sender, lab.inspectTarget(player));
            case "set" -> {
                requireArgs(args, 5, "/ws test mob set <stat> <value>");
                lab.setTargetNumber(player, args[3], parseDouble(args[4], args[3]));
            }
            case "flag" -> {
                requireArgs(args, 5, "/ws test mob flag <ai|invulnerable|glowing> <true|false>");
                lab.setTargetFlag(player, args[3], parseBoolean(args[4]));
            }
            case "status" -> {
                requireArgs(args, 4, "/ws test mob status <add|clear>");
                if ("clear".equalsIgnoreCase(args[3])) {
                    lab.clearTargetStatuses(player);
                } else {
                    requireArgs(args, 6, "/ws test mob status add <id> <durationTicks> [amplifier]");
                    lab.addTargetStatus(player, args[4], parseInt(args[5], "durationTicks"),
                            args.length >= 7 ? parseInt(args[6], "amplifier") : 0);
                }
            }
            case "phase" -> {
                requireArgs(args, 4, "/ws test mob phase 2");
                if (parseInt(args[3], "phase") != 2) {
                    throw new IllegalArgumentException("Prototype Test Lab only forces boss phase 2");
                }
                lab.forceBossPhaseTwo(player);
            }
            case "pattern" -> lab.forceBossPattern(player);
            default -> throw new IllegalArgumentException("Unknown mob operation " + args[2]);
        }
    }

    private void world(CommandSender sender, String[] args) throws IOException {
        requirePermission(sender, "wildsurvival.test.mutate");
        requireArgs(args, 4, "/ws test world <day|time|weather> ...");
        Player player = requirePlayer(sender);
        switch (lower(args[2])) {
            case "day" -> {
                if ("advance".equalsIgnoreCase(args[3])) {
                    runs.forceAdvance();
                } else {
                    requireArgs(args, 5, "/ws test world day set <1|3|6|10>");
                    lab.setDay(player, parseInt(args[4], "day"));
                }
            }
            case "time" -> {
                switch (lower(args[3])) {
                    case "freeze" -> lab.setTimeFrozen(player, true);
                    case "resume" -> lab.setTimeFrozen(player, false);
                    case "scale" -> {
                        requireArgs(args, 5, "/ws test world time scale <0.05..100>");
                        lab.setTimeScale(player, parseDouble(args[4], "time scale"));
                    }
                    case "step" -> {
                        requireArgs(args, 5, "/ws test world time step <ticks>");
                        lab.stepTime(player, parseLong(args[4], "ticks"));
                    }
                    default -> throw new IllegalArgumentException("Unknown time operation " + args[3]);
                }
            }
            case "weather" -> lab.setWeather(player, args[3]);
            default -> throw new IllegalArgumentException("Unknown world operation " + args[2]);
        }
    }

    private void party(CommandSender sender, String[] args) throws IOException {
        requirePermission(sender, "wildsurvival.test.mutate");
        requireArgs(args, 4, "/ws test party <size|contribute|boss-channel|dummy> ...");
        Player player = requirePlayer(sender);
        switch (lower(args[2])) {
            case "size" -> lab.setVirtualPartySize(player, parseInt(args[3], "size"));
            case "contribute" -> {
                requireArgs(args, 5, "/ws test party contribute <category> <amount>");
                lab.setVirtualContribution(player, args[3], parseInt(args[4], "amount"));
            }
            case "boss-channel" -> lab.simulateBossContributors(player, parseInt(args[3], "contributors"));
            case "dummy" -> {
                switch (lower(args[3])) {
                    case "spawn" -> {
                        requireArgs(args, 6, "/ws test party dummy spawn <id> <ACTIVE|DOWNED|DEAD>");
                        virtualParty.spawn(player, args[4], args[5]);
                    }
                    case "list" -> sender.sendMessage(ChatColor.AQUA + "Dummies: " + virtualParty.list());
                    case "clear" -> sender.sendMessage(ChatColor.YELLOW + "Removed " + virtualParty.clear() + " dummies");
                    default -> throw new IllegalArgumentException("Unknown dummy operation " + args[3]);
                }
            }
            default -> throw new IllegalArgumentException("Unknown party operation " + args[2]);
        }
    }

    private void damage(CommandSender sender, String[] args) {
        requirePermission(sender, "wildsurvival.test.inspect");
        requireArgs(args, 5, "/ws test damage preview <rawDamage> <rawBreak>");
        if (!"preview".equalsIgnoreCase(args[2])) {
            throw new IllegalArgumentException("Only damage preview is supported");
        }
        Player player = requirePlayer(sender);
        CombatService.DamagePreview preview = combat.previewDamage(player, lab.target(player),
                parseDouble(args[3], "rawDamage"), parseDouble(args[4], "rawBreak"));
        sendObject(sender, preview);
    }

    private void inspect(CommandSender sender, String[] args) throws IOException {
        requirePermission(sender, "wildsurvival.test.inspect");
        String scope = args.length >= 3 ? lower(args[2]) : "run";
        switch (scope) {
            case "run" -> sender.sendMessage(ChatColor.AQUA + lab.summary());
            case "player" -> sendMap(sender, lab.playerView(requirePlayer(sender)));
            case "target" -> sendObject(sender, lab.inspectTarget(requirePlayer(sender)));
            case "dummies" -> sender.sendMessage(ChatColor.AQUA + virtualParty.list().toString());
            default -> throw new IllegalArgumentException("Inspect scope must be run, player, target, or dummies");
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "WildSurvival Test Lab");
        sender.sendMessage(ChatColor.WHITE + "/ws test enter [seed] [partySize] | gui | status");
        sender.sendMessage(ChatColor.WHITE + "/ws test player|item|augment|mob|world|party|damage|inspect ...");
        sender.sendMessage(ChatColor.WHITE + "/ws test preset|scenario|snapshot|undo|export");
        sender.sendMessage(ChatColor.RED + "/ws test reset --confirm | exit <reason> --confirm");
    }

    private List<String> safePresets(String prefix) {
        try {
            return filter(prefix, lab.listPresets());
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private static void sendMap(CommandSender sender, Map<String, Object> values) {
        values.forEach((key, value) -> sender.sendMessage(ChatColor.GRAY + key + "=" + ChatColor.WHITE + value));
    }

    private static void sendObject(CommandSender sender, Object value) {
        sender.sendMessage(ChatColor.AQUA + String.valueOf(value));
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("Player-only Test Lab command");
        }
        return player;
    }

    private static void requirePermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            throw new SecurityException("Missing permission " + permission);
        }
    }

    private static void requireArgs(String[] args, int minimum, String usage) {
        if (args.length < minimum) {
            throw new IllegalArgumentException(usage);
        }
    }

    private static String required(String[] args, int index) {
        if (args.length <= index) {
            throw new IllegalArgumentException("Missing value at argument " + index);
        }
        return args[index];
    }

    private static boolean contains(String[] args, String expected) {
        for (String value : args) {
            if (expected.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static int parseInt(String raw, String label) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be an integer");
        }
    }

    private static long parseLong(String raw, String label) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be an integer");
        }
    }

    private static double parseDouble(String raw, String label) {
        try {
            double value = Double.parseDouble(raw);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a finite number");
        }
    }

    private static boolean parseBoolean(String raw) {
        if (!"true".equalsIgnoreCase(raw) && !"false".equalsIgnoreCase(raw)) {
            throw new IllegalArgumentException("Boolean value must be true or false");
        }
        return Boolean.parseBoolean(raw);
    }

    private static double defaultStat(String rawId) {
        return switch (TestValuePolicy.statId(rawId)) {
            case "damage-reduction" -> 0.0;
            case "max-health" -> 20.0;
            case "max-ap" -> 100.0;
            default -> 1.0;
        };
    }

    private static List<String> filter(String prefix, Collection<String> candidates) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
                .sorted().toList();
    }
}
