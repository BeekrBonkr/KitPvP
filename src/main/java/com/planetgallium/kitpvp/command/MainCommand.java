package com.planetgallium.kitpvp.command;

import com.planetgallium.database.TopEntry;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.api.Kit;
import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.util.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainCommand implements CommandExecutor {

    /** Players waiting on a /kp spawn countdown, keyed by UUID so a rejoin cannot get stuck. */
    private final Map<UUID, BukkitRunnable> spawnCountdowns = new HashMap<>();

    /** Destructive commands ask once; the pending confirmation is remembered here. */
    private final Map<UUID, String> pendingConfirmations = new HashMap<>();

    private final Game plugin;
    private final Arena arena;
    private final Resources resources;
    private final Resource config;
    private final Resource messages;

    public MainCommand(Game game) {
        this.plugin = game;
        this.arena = game.getArena();
        this.resources = game.getResources();
        this.config = resources.getConfig();
        this.messages = resources.getMessages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 0) {
            executeInfoCommand(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                executeHelpCommand(sender);
                return true;

            case "reload":
                if (hasPermission(sender, "kp.command.reload")) executeReloadCommand(sender);
                return true;

            case "debug":
                if (hasPermission(sender, "kp.command.debug")) executeDebugCommand(sender);
                return true;

            case "kits":
                if (hasPermission(sender, "kp.command.kits")) executeKitsCommand(sender);
                return true;

            case "top":
                if (hasPermission(sender, "kp.command.top")) executeTopCommand(sender, args);
                return true;

            case "stats":
                if (args.length == 1) {
                    if (requirePlayer(sender) && hasPermission(sender, "kp.command.stats")) {
                        sendStatsMessage(sender, ((Player) sender).getName());
                    }
                } else if (hasPermission(sender, "kp.command.stats.other")) {
                    executeStatsCommandOther(sender, args);
                }
                return true;

            case "clear":
                if (args.length == 1) {
                    if (requirePlayer(sender) && hasPermission(sender, "kp.command.clear")) {
                        executeClearCommandSelf((Player) sender);
                    }
                } else if (hasPermission(sender, "kp.command.clear.other")) {
                    executeClearCommandOther(sender, args);
                }
                return true;

            case "delete":
                if (args.length < 2) {
                    sendUsage(sender, "/" + alias + " delete <kit>");
                } else if (hasPermission(sender, "kp.command.delete")) {
                    executeDeleteKitCommand(sender, args);
                }
                return true;

            case "kit":
                if (args.length == 2) {
                    if (requirePlayer(sender)) executeKitCommandSelf((Player) sender, args);
                } else if (args.length >= 3) {
                    if (hasPermission(sender, "kp.command.kit.other")) executeKitCommand(sender, args);
                } else {
                    sendUsage(sender, "/" + alias + " kit <kit> [player]");
                }
                return true;

            case "setstats":
                if (args.length < 4) {
                    sendUsage(sender, "/" + alias + " setstats <player> <type> <amount>");
                } else if (hasPermission(sender, "kp.command.setstats")) {
                    executeSetStatsCommand(sender, args);
                }
                return true;

            case "menu":
                if (requirePlayer(sender) && hasPermission(sender, "kp.command.menu")) {
                    arena.getMenus().getKitMenu().open((Player) sender);
                }
                return true;

            case "spawn":
                if (requirePlayer(sender) && hasPermission(sender, "kp.command.spawn")) {
                    executeSpawnCommand((Player) sender);
                }
                return true;

            case "addspawn":
                if (requirePlayer(sender) && hasPermission(sender, "kp.command.addspawn")) {
                    executeAddSpawnCommand((Player) sender);
                }
                return true;

            case "delarena":
                if (requirePlayer(sender) && hasPermission(sender, "kp.command.delarena")) {
                    executeDeleteArenaCommand((Player) sender);
                }
                return true;

            case "arena":
                if (args.length < 2) {
                    sendUsage(sender, "/" + alias + " arena <arena>");
                } else if (requirePlayer(sender) && hasPermission(sender, "kp.command.arena")) {
                    executeArenaCommand((Player) sender, args);
                }
                return true;

            case "preview":
                if (args.length < 2) {
                    sendUsage(sender, "/" + alias + " preview <kit>");
                } else if (requirePlayer(sender) && hasPermission(sender, "kp.command.preview")) {
                    executePreviewCommand((Player) sender, args);
                }
                return true;

            case "create":
                if (args.length < 2) {
                    sendUsage(sender, "/" + alias + " create <kit>");
                } else if (requirePlayer(sender) && hasPermission(sender, "kp.command.create")) {
                    executeCreateKitCommand((Player) sender, args);
                }
                return true;

            default:
                sendUnknownCommand(sender, alias, args);
                return true;
        }
    }

    private void executeInfoCommand(CommandSender sender) {
        sender.sendMessage(Toolkit.translate("&7[&b&lKIT-PVP&7]"));
        sender.sendMessage(Toolkit.translate("&7Version: &b" + plugin.getDescription().getVersion()));
        sender.sendMessage(Toolkit.translate("&7Developer: &bCervinakuy"));
        sender.sendMessage(Toolkit.translate("&7Commands: &b/kp help"));
        sender.sendMessage(Toolkit.translate("&7Download: &bbit.ly/KP-Download"));
    }

    /** Only lists what the sender can actually run. */
    private void executeHelpCommand(CommandSender sender) {
        List<String> lines = new ArrayList<>();

        addHelpLine(sender, lines, null, "/kp", "Displays information about KitPvP.");
        addHelpLine(sender, lines, null, "/kp help", "Displays this message.");
        addHelpLine(sender, lines, "kp.command.menu", "/kp menu", "Opens the kit menu.");
        addHelpLine(sender, lines, null, "/kp kit <kit>", "Select a kit.");
        addHelpLine(sender, lines, "kp.command.kits", "/kp kits", "Lists all available kits.");
        addHelpLine(sender, lines, "kp.command.preview", "/kp preview <kit>", "Preview the contents of a kit.");
        addHelpLine(sender, lines, "kp.command.clear", "/kp clear", "Clears your current kit.");
        addHelpLine(sender, lines, "kp.command.stats", "/kp stats", "View your stats.");
        addHelpLine(sender, lines, "kp.command.top", "/kp top <kills|deaths|level>", "View the leaderboard.");
        addHelpLine(sender, lines, "kp.command.spawn", "/kp spawn", "Teleports you to the arena spawn.");
        addHelpLine(sender, lines, "kp.command.arena", "/kp arena <arena>", "Teleports you to another arena.");
        addHelpLine(sender, lines, "kp.command.stats.other", "/kp stats <player>", "View another player's stats.");
        addHelpLine(sender, lines, "kp.command.clear.other", "/kp clear <player>", "Clears a player's kit.");
        addHelpLine(sender, lines, "kp.command.kit.other", "/kp kit <kit> <player>", "Gives a player a kit.");
        addHelpLine(sender, lines, "kp.command.create", "/kp create <kit>", "Creates a kit from your inventory.");
        addHelpLine(sender, lines, "kp.command.delete", "/kp delete <kit>", "Deletes an existing kit.");
        addHelpLine(sender, lines, "kp.command.addspawn", "/kp addspawn", "Adds a spawn to this world's arena.");
        addHelpLine(sender, lines, "kp.command.delarena", "/kp delarena", "Removes this world's arena.");
        addHelpLine(sender, lines, "kp.command.setstats", "/kp setstats <player> <type> <amount>",
                "Change a player's stats.");
        addHelpLine(sender, lines, "kp.command.reload", "/kp reload", "Reloads the configuration files.");
        addHelpLine(sender, lines, "kp.command.debug", "/kp debug", "Prints debug information.");

        sender.sendMessage(Toolkit.translate("&3&m           &r &b&lKIT-PVP &3Created by Cervinakuy &3&m             "));
        sender.sendMessage(Toolkit.translate(" "));
        for (String line : lines) {
            sender.sendMessage(line);
        }
        sender.sendMessage(Toolkit.translate(" "));
        sender.sendMessage(Toolkit.translate("&3&m                                                                               "));
    }

    private void addHelpLine(CommandSender sender, List<String> lines, String permission, String usage,
                             String description) {
        if (permission == null || sender.hasPermission(permission)) {
            lines.add(Toolkit.translate("&7- &b" + usage + " &7" + description));
        }
    }

    private void executeReloadCommand(CommandSender sender) {
        resources.reload();
        CacheManager.clearCaches();
        Toolkit.clearWarningHistory();
        arena.getAbilities().rebuildCache();
        arena.getKits().validateLoadedKits();
        arena.startScoreboardRefresh();

        sender.sendMessage(messages.fetchString("Messages.Commands.Reload"));
    }

    private void executeDebugCommand(CommandSender sender) {
        List<String> specialPlugins = Arrays.asList("PerWorldPlugins", "AuthMe");

        StringBuilder names = new StringBuilder();

        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            String pluginName = plugin.getName();
            if (specialPlugins.contains(pluginName) || pluginName.toLowerCase().contains("auth")) {
                names.append("&c");
            }
            names.append(plugin.getName()).append(" ");
        }

        String serverVersion = Bukkit.getBukkitVersion() + " " +
                (Bukkit.getVersion().contains("Spigot") ? "(Spigot)" : "(Other)");
        String pluginVersion = plugin.getDescription().getVersion() + " " +
                (plugin.needsUpdate() ? "&c(Requires Update)" : "&e(Latest Version)");
        String isSpawnSet = (config.contains("Arenas") ? "&eConfigured" : "&cUnconfigured");

        sender.sendMessage(Toolkit.translate("&7[&b&lKIT-PVP&7] &aServer Version: &7" + serverVersion));
        sender.sendMessage(Toolkit.translate("&7[&b&lKIT-PVP&7] &aDetected As: &7" + Toolkit.versionToNumber()));
        sender.sendMessage(Toolkit.translate("&7[&b&lKIT-PVP&7] &aPlugin Version: &7" + pluginVersion));
        sender.sendMessage(Toolkit.translate("&7[&b&lKIT-PVP&7] &aSpawn Set: " + isSpawnSet));
        sender.sendMessage(Toolkit.translate("&7[&b&lKIT-PVP&7] &aStorage: &7" +
                config.fetchString("Storage.Type")));
        sender.sendMessage(Toolkit.translate("&7[&b&lKIT-PVP&7] &aKits: &7" + arena.getKits().getKitNames().size()));
        sender.sendMessage(Toolkit.translate("&7[&b&lKIT-PVP&7] &aPlugin List: &7" + names));
    }

    private void executeKitsCommand(CommandSender sender) {
        List<String> kitNames = arena.getKits().getKitNames();

        if (kitNames.isEmpty()) {
            sender.sendMessage(messages.fetchString("Messages.Error.NoKits"));
            return;
        }

        sender.sendMessage(messages.fetchString("Messages.Commands.Kits")
                .replace("%kits%", String.join(", ", kitNames)));
    }

    private void executeTopCommand(CommandSender sender, String[] args) {
        String type = args.length > 1 ? args[1].toLowerCase() : "kills";

        if (!isValidStatIdentifier(type, false)) {
            sender.sendMessage(messages.fetchString("Messages.Error.InvalidType")
                    .replace("%type%", type)
                    .replace("%types%", "kills, deaths, level"));
            return;
        }

        sender.sendMessage(messages.fetchString("Messages.Commands.TopHeader")
                .replace("%type%", type));

        for (int rank = 1; rank <= 10; rank++) {
            TopEntry entry = arena.getLeaderboards().getTopN(type, rank);

            if (entry == null || entry.getValue() <= 0) {
                continue;
            }

            sender.sendMessage(messages.fetchString("Messages.Commands.TopEntry")
                    .replace("%rank%", String.valueOf(rank))
                    .replace("%player%", entry.getIdentifier())
                    .replace("%amount%", String.valueOf(entry.getValue())));
        }
    }

    private void executeClearCommandOther(CommandSender sender, String[] args) {
        String playerName = args[1];

        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendMessage(messages.fetchString("Messages.Error.Offline"));
            return;
        }

        if (!Toolkit.inArena(target)) {
            sender.sendMessage(messages.fetchString("Messages.Error.LocationOther")
                    .replace("%player%", target.getName()));
            return;
        }

        clearKit(target);

        target.sendMessage(messages.fetchString("Messages.Commands.Cleared"));
        sender.sendMessage(messages.fetchString("Messages.Commands.ClearedOther")
                .replace("%player%", target.getName()));
    }

    private void executeDeleteKitCommand(CommandSender sender, String[] args) {
        String kitName = args[1];

        if (!arena.getKits().isKit(kitName)) {
            sender.sendMessage(messages.fetchString("Messages.Error.Lost"));
            return;
        }

        String resolvedName = arena.getKits().resolveKitName(kitName);

        // deleting a kit removes its file for good, so it is confirmed once first
        if (!confirm(sender, "delete:" + resolvedName,
                messages.fetchString("Messages.Commands.ConfirmDelete").replace("%kit%", resolvedName))) {
            return;
        }

        arena.getKits().deleteKit(resolvedName);
        sender.sendMessage(messages.fetchString("Messages.Commands.Delete")
                .replace("%kit%", resolvedName));
    }

    private void executeStatsCommandOther(CommandSender sender, String[] args) {
        String targetName = args[1];

        if (plugin.getDatabase().isPlayerRegistered(targetName)) {
            sendStatsMessage(sender, targetName);
        } else {
            sender.sendMessage(messages.fetchString("Messages.Error.NoStats")
                    .replace("%player%", targetName));
        }
    }

    private void executeKitCommand(CommandSender sender, String[] args) {
        String kitName = args[1];
        String playerName = args[2];

        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendMessage(messages.fetchString("Messages.Error.Offline"));
            return;
        }

        if (!Toolkit.inArena(target)) {
            sender.sendMessage(messages.fetchString("Messages.Error.LocationOther")
                    .replace("%player%", target.getName()));
            return;
        }

        if (arena.getKits().playerHasKit(target.getName())) {
            sender.sendMessage(messages.fetchString("Messages.Error.SelectedOther"));
            return;
        }

        Kit kitToGive = arena.getKits().getKitByName(kitName);

        if (arena.getKits().attemptToGiveKitToPlayer(target, kitToGive)) {
            sender.sendMessage(messages.fetchString("Messages.Commands.KitOther")
                    .replace("%player%", target.getName())
                    .replace("%kit%", kitToGive.getName()));
        }
    }

    private void executeSetStatsCommand(CommandSender sender, String[] args) {
        String playerName = args[1];
        String statsIdentifier = args[2].toLowerCase();
        String possibleAmount = args[3];

        if (!isValidStatIdentifier(statsIdentifier, true)) {
            sender.sendMessage(messages.fetchString("Messages.Error.InvalidType")
                    .replace("%type%", statsIdentifier)
                    .replace("%types%", "kills, deaths, level, experience"));
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(possibleAmount);
        } catch (NumberFormatException e) {
            sender.sendMessage(messages.fetchString("Messages.Error.InvalidNumber")
                    .replace("%number%", possibleAmount));
            return;
        }

        if (amount < 0) {
            sender.sendMessage(messages.fetchString("Messages.Error.InvalidNumber")
                    .replace("%number%", possibleAmount));
            return;
        }

        if (plugin.getDatabase().usernameToUUID(playerName) == null) {
            sender.sendMessage(messages.fetchString("Messages.Error.NoStats")
                    .replace("%player%", playerName));
            return;
        }

        arena.getStats().setStat(statsIdentifier, playerName, amount);
        arena.getStats().pushCachedStatsToDatabase(playerName, false);

        sender.sendMessage(messages.fetchString("Messages.Commands.SetStats")
                .replace("%player%", playerName)
                .replace("%amount%", String.valueOf(amount))
                .replace("%type%", statsIdentifier));
    }

    private void executeClearCommandSelf(Player p) {
        // clearing a kit is one of the ways out of a losing fight, so it waits
        // until the player is out of combat
        if (!p.hasPermission("kp.combattag.bypass") && plugin.getCombatTagger().denyIfTagged(p)) {
            return;
        }

        clearKit(p);
        p.sendMessage(messages.fetchString("Messages.Commands.Cleared"));
    }

    private void executeSpawnCommand(Player p) {
        if (!config.getBoolean("Spawn.Enabled")) {
            p.sendMessage(messages.fetchString("Messages.Error.Disabled"));
            return;
        }

        if (!config.contains("Arenas." + p.getWorld().getName())) {
            p.sendMessage(messages.fetchString("Messages.Error.Arena")
                    .replace("%arena%", p.getWorld().getName()));
            return;
        }

        if (!p.hasPermission("kp.combattag.bypass") && plugin.getCombatTagger().denyIfTagged(p)) {
            return;
        }

        if (spawnCountdowns.containsKey(p.getUniqueId())) {
            p.sendMessage(messages.fetchString("Messages.Error.AlreadyTeleporting"));
            return;
        }

        p.sendMessage(messages.fetchString("Messages.Commands.Teleporting"));
        Toolkit.playSoundToPlayer(p, "ENTITY_ITEM_PICKUP", 1);

        Location beforeLocation = p.getLocation();

        BukkitRunnable countdown = new BukkitRunnable() {
            public int time = config.getInt("Spawn.Time") + 1;

            @Override
            public void run() {
                // a player who logs out mid-countdown used to stay on the waiting list
                // for good, which made /kp spawn silently do nothing when they returned
                if (!p.isOnline()) {
                    finish(p);
                    return;
                }

                time--;

                if (time > 0) {
                    if (p.getGameMode() == GameMode.SPECTATOR) {
                        finish(p);
                        return;
                    }

                    p.sendMessage(messages.fetchString("Messages.Commands.Time")
                            .replace("%time%", String.valueOf(time)));
                    Toolkit.playSoundToPlayer(p, "BLOCK_NOTE_BLOCK_SNARE", 1);

                    if (beforeLocation.getBlockX() != p.getLocation().getBlockX() ||
                            beforeLocation.getBlockY() != p.getLocation().getBlockY() ||
                            beforeLocation.getBlockZ() != p.getLocation().getBlockZ()) {
                        p.sendMessage(messages.fetchString("Messages.Error.Moved"));
                        finish(p);
                    }
                } else {
                    p.sendMessage(messages.fetchString("Messages.Commands.Teleport"));

                    arena.toSpawn(p, p.getWorld().getName());

                    if (config.getBoolean("Arena.ClearKitOnCommandSpawn")) {
                        clearKit(p);
                    }

                    Toolkit.playSoundToPlayer(p, "ENTITY_ENDERMAN_TELEPORT", 1);
                    finish(p);
                }
            }

            private void finish(Player player) {
                spawnCountdowns.remove(player.getUniqueId());
                cancel();
            }
        };

        spawnCountdowns.put(p.getUniqueId(), countdown);
        countdown.runTaskTimer(plugin, 0L, 20L);
    }

    /** Called when a player quits, so a countdown never outlives the player it belongs to. */
    public void cancelSpawnCountdown(UUID playerId) {
        BukkitRunnable countdown = spawnCountdowns.remove(playerId);
        if (countdown != null) {
            countdown.cancel();
        }
        pendingConfirmations.remove(playerId);
    }

    private void executeAddSpawnCommand(Player p) {
        String arenaName = p.getWorld().getName();
        int spawnNumber = Toolkit.getNextAvailable(config, "Arenas." + arenaName,
                1000, false, 1);

        Toolkit.saveLocationToResource(config, "Arenas." + arenaName + "." + spawnNumber, p.getLocation());

        p.sendMessage(messages.fetchString("Messages.Commands.Added")
                .replace("%number%", String.valueOf(spawnNumber))
                .replace("%arena%", arenaName));
        Toolkit.playSoundToPlayer(p, "ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR", 1);
    }

    private void executeDeleteArenaCommand(Player p) {
        String arenaName = p.getWorld().getName();

        if (!config.contains("Arenas." + arenaName)) {
            p.sendMessage(messages.fetchString("Messages.Error.Arena").replace("%arena%", arenaName));
            return;
        }

        int spawnCount = config.getConfigurationSection("Arenas." + arenaName) == null ? 0 :
                config.getConfigurationSection("Arenas." + arenaName).getKeys(false).size();

        if (!confirm(p, "delarena:" + arenaName,
                messages.fetchString("Messages.Commands.ConfirmDeleteArena")
                        .replace("%arena%", arenaName)
                        .replace("%number%", String.valueOf(spawnCount)))) {
            return;
        }

        config.set("Arenas." + arenaName, null);
        config.save(); // the plugin's own config object, not JavaPlugin's separate copy

        p.sendMessage(messages.fetchString("Messages.Commands.Removed").replace("%arena%", arenaName));
        Toolkit.playSoundToPlayer(p, "ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR", 1);
    }

    /**
     * Returns true once the sender has repeated a destructive command. The first
     * attempt explains what is about to happen and waits.
     */
    private boolean confirm(CommandSender sender, String action, String warning) {
        if (!(sender instanceof Player)) {
            return true; // the console is assumed to know what it typed
        }

        UUID senderId = ((Player) sender).getUniqueId();

        if (action.equals(pendingConfirmations.get(senderId))) {
            pendingConfirmations.remove(senderId);
            return true;
        }

        pendingConfirmations.put(senderId, action);
        sender.sendMessage(warning);
        return false;
    }

    private void executeArenaCommand(Player p, String[] args) {
        String arenaName = args[1];

        if (resources.getConfig().getBoolean("Arena.PreventArenaSignUseWithKit")) {
            if (arena.getKits().playerHasKit(p.getName())) {
                p.sendMessage(messages.fetchString("Messages.Error.KitInvalid"));
                return;
            }
        }

        if (!p.hasPermission("kp.combattag.bypass") && plugin.getCombatTagger().denyIfTagged(p)) {
            return;
        }

        if (!config.contains("Arenas." + arenaName)) {
            p.sendMessage(messages.fetchString("Messages.Error.ExistsArena"));
            return;
        }

        arena.toSpawn(p, arenaName);
    }

    private void executePreviewCommand(Player p, String[] args) {
        String kitName = args[1];

        if (arena.getKits().isKit(kitName)) {
            Kit kitToPreview = arena.getKits().getKitByName(kitName);
            if (kitToPreview != null) {
                arena.getMenus().getPreviewMenu().open(p, kitToPreview, resources);
                return;
            }
        }
        p.sendMessage(messages.fetchString("Messages.Error.Lost"));
    }

    private void executeCreateKitCommand(Player p, String[] args) {
        String kitName = args[1];

        if (!validateKitName(kitName)) {
            p.sendMessage(messages.fetchString("Messages.Error.InvalidKitName"));
            return;
        }

        if (!arena.getKits().isKit(kitName)) {
            arena.getKits().createKit(p, kitName);

            p.sendMessage(messages.fetchString("Messages.Commands.Create")
                    .replace("%kit%", kitName));
        } else {
            p.sendMessage(messages.fetchString("Messages.Error.Exists"));
        }
    }

    private void executeKitCommandSelf(Player p, String[] args) {
        if (Toolkit.inArena(p)) {
            String kitName = args[1];
            Kit kitToGive = arena.getKits().getKitByName(kitName);

            arena.getKits().attemptToGiveKitToPlayer(p, kitToGive);
        } else {
            p.sendMessage(messages.fetchString("Messages.Error.Location"));
        }
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }

        sender.sendMessage(messages.fetchString("Messages.General.Permission")
                .replace("%permission%", permission));
        return false;
    }

    private boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return true;
        }
        sender.sendMessage(messages.fetchString("Messages.General.Player"));
        return false;
    }

    private void clearKit(Player p) {
        CacheManager.getPotionSwitcherUsers().remove(p.getName());

        p.getInventory().setArmorContents(null);
        p.getInventory().clear();

        // resetting max health is not the same as healing: refilling the player's
        // health here turned /kp clear into a free full heal mid-fight
        double healthBeforeReset = p.getHealth();
        Toolkit.setMaxHealth(p, 20);
        p.setHealth(Math.min(healthBeforeReset, Toolkit.getMaxHealth(p)));

        for (PotionEffect effect : p.getActivePotionEffects()) {
            p.removePotionEffect(effect.getType());
        }

        if (config.getBoolean("Arena.GiveItemsOnClear")) {
            arena.giveArenaItems(p);
        }

        arena.getKits().resetPlayerKit(p.getName());
    }

    private void sendStatsMessage(CommandSender receiver, String username) {
        for (String line : messages.getStringList("Messages.Stats.Message")) {
            receiver.sendMessage(arena.getUtilities().replaceBuiltInPlaceholdersIfPresent(line, username));
        }
    }

    /** Kit names become file names and database table names, so they stay plain. */
    private boolean validateKitName(String possibleKitName) {
        if (possibleKitName == null || possibleKitName.isEmpty() || possibleKitName.length() > 32) {
            return false;
        }

        for (char c : possibleKitName.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String usage) {
        sender.sendMessage(messages.fetchString("Messages.Error.Arguments").replace("%usage%", usage));
    }

    private void sendUnknownCommand(CommandSender sender, String alias, String[] args) {
        StringBuilder unknownCommand = new StringBuilder();
        for (String arg : args) {
            unknownCommand.append(arg).append(" ");
        }

        sender.sendMessage(Toolkit.translate("%prefix% &cUnknown command: /" + alias + " " +
                unknownCommand.toString().trim() + ". Try &f/" + alias + " help&c."));
    }

    private boolean isValidStatIdentifier(String identifierToValidate, boolean includeExperience) {
        return (identifierToValidate.equalsIgnoreCase("kills") ||
                identifierToValidate.equalsIgnoreCase("deaths") ||
                identifierToValidate.equalsIgnoreCase("level")) ||
                (includeExperience && identifierToValidate.equalsIgnoreCase("experience"));
    }

}
