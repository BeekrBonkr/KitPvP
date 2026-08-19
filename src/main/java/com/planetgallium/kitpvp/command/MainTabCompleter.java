package com.planetgallium.kitpvp.command;

import com.planetgallium.kitpvp.Game;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Completion for /kitpvp. Kit names are case sensitive to look at but not to
 * type, and completing them is what stops most "kit not found" mistakes before
 * they happen.
 */
public class MainTabCompleter implements TabCompleter {

	private static final List<String> STAT_TYPES = Arrays.asList("kills", "deaths", "level", "experience");
	private static final List<String> TOP_TYPES = Arrays.asList("kills", "deaths", "level");

	private final Game plugin;

	public MainTabCompleter(Game plugin) {
		this.plugin = plugin;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return matching(args[0], subCommandsFor(sender));
		}

		String subCommand = args[0].toLowerCase();

		if (args.length == 2) {
			switch (subCommand) {
				case "kit":
				case "preview":
				case "delete":
					return matching(args[1], plugin.getArena().getKits().getKitNames());
				case "arena":
					return matching(args[1], arenaNames());
				case "top":
					return matching(args[1], TOP_TYPES);
				case "stats":
				case "clear":
				case "setstats":
					return matching(args[1], onlinePlayerNames(sender));
				default:
					return Collections.emptyList();
			}
		}

		if (args.length == 3) {
			if (subCommand.equals("kit")) {
				return matching(args[2], onlinePlayerNames(sender));
			}
			if (subCommand.equals("setstats")) {
				return matching(args[2], STAT_TYPES);
			}
		}

		return Collections.emptyList();
	}

	private List<String> subCommandsFor(CommandSender sender) {
		List<String> available = new ArrayList<>(Arrays.asList("help", "kit", "kits", "menu", "preview", "stats"));

		addIfPermitted(sender, available, "kp.command.top", "top");
		addIfPermitted(sender, available, "kp.command.clear", "clear");
		addIfPermitted(sender, available, "kp.command.spawn", "spawn");
		addIfPermitted(sender, available, "kp.command.arena", "arena");
		addIfPermitted(sender, available, "kp.command.create", "create");
		addIfPermitted(sender, available, "kp.command.delete", "delete");
		addIfPermitted(sender, available, "kp.command.addspawn", "addspawn");
		addIfPermitted(sender, available, "kp.command.delarena", "delarena");
		addIfPermitted(sender, available, "kp.command.setstats", "setstats");
		addIfPermitted(sender, available, "kp.command.reload", "reload");
		addIfPermitted(sender, available, "kp.command.debug", "debug");

		return available;
	}

	private void addIfPermitted(CommandSender sender, List<String> target, String permission, String subCommand) {
		if (sender.hasPermission(permission)) {
			target.add(subCommand);
		}
	}

	private List<String> arenaNames() {
		if (!plugin.getResources().getConfig().contains("Arenas") ||
				plugin.getResources().getConfig().getConfigurationSection("Arenas") == null) {
			return Collections.emptyList();
		}
		return new ArrayList<>(plugin.getResources().getConfig().getConfigurationSection("Arenas").getKeys(false));
	}

	/** Players the sender can already see, so completion cannot reveal a vanished player. */
	private List<String> onlinePlayerNames(CommandSender sender) {
		List<String> names = new ArrayList<>();

		for (Player online : Bukkit.getOnlinePlayers()) {
			if (sender instanceof Player && !((Player) sender).canSee(online)) {
				continue;
			}
			names.add(online.getName());
		}
		return names;
	}

	private List<String> matching(String typedSoFar, List<String> candidates) {
		List<String> matches = new ArrayList<>();
		String lowered = typedSoFar.toLowerCase();

		for (String candidate : candidates) {
			if (candidate.toLowerCase().startsWith(lowered)) {
				matches.add(candidate);
			}
		}

		Collections.sort(matches);
		return matches;
	}

}
