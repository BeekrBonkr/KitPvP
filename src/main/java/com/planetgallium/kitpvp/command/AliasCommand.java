package com.planetgallium.kitpvp.command;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.util.Resource;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Optional short commands. Each alias matches the typed command exactly:
 * "/kit" used to be matched with startsWith, which swallowed the plugin's own
 * /kitpvp command along with anything else beginning with those letters.
 */
public class AliasCommand implements Listener {

	private final Resource config;

	public AliasCommand(Game plugin) {
		this.config = plugin.getResources().getConfig();
	}

	@EventHandler
	public void onCommand(PlayerCommandPreprocessEvent e) {
		Player p = e.getPlayer();

		String message = e.getMessage();
		if (!message.startsWith("/")) {
			return;
		}

		String[] words = message.substring(1).trim().split("\\s+");
		if (words.length == 0 || words.length > 2) {
			return; // the aliases take at most a single argument
		}

		String typedCommand = words[0].toLowerCase();
		String argument = words.length > 1 ? words[1] : null;

		if (typedCommand.equals("spawn") && argument == null && config.getBoolean("Commands.Alias.Spawn")) {
			e.setCancelled(true);
			p.performCommand("kp spawn");

		} else if (typedCommand.equals("kits") && argument == null && config.getBoolean("Commands.Alias.Kits")) {
			e.setCancelled(true);
			p.performCommand("kp kits");

		} else if (typedCommand.equals("kit") && config.getBoolean("Commands.Alias.Kit")) {
			e.setCancelled(true);
			p.performCommand(argument == null ? "kp menu" : "kp kit " + argument);

		} else if (typedCommand.equals("stats") && config.getBoolean("Commands.Alias.Stats")) {
			e.setCancelled(true);
			p.performCommand(argument == null ? "kp stats" : "kp stats " + argument);
		}
	}

}
