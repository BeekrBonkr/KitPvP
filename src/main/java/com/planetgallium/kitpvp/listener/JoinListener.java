package com.planetgallium.kitpvp.listener;

import com.planetgallium.kitpvp.util.CacheManager;
import com.planetgallium.kitpvp.util.Resource;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.util.Toolkit;

public class JoinListener implements Listener {

	private final Game plugin;
	private final Arena arena;
	private final Resource config;

	public JoinListener(Game plugin) {
		this.plugin = plugin;
		this.arena = plugin.getArena();
		this.config = plugin.getResources().getConfig();
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		Player p = e.getPlayer();

		// Update checker
		if (plugin.needsUpdate() && p.hasPermission("kp.updatenotify")) {
			p.sendMessage(Toolkit.translate("&7[&b&lKIT-PVP&7] &aAn update was found: v" +
					plugin.getUpdateVersion() + " https://www.spigotmc.org/resources/27107/"));
		}

		arena.getStats().createPlayer(p);

		if (Toolkit.inArena(p)) {
			enterArena(p);
		}
	}

	/**
	 * One handler for world changes, rather than one here adding the player and
	 * another elsewhere removing them again a moment later.
	 */
	@EventHandler
	public void onWorldChange(PlayerChangedWorldEvent e) {
		Player p = e.getPlayer();

		boolean cameFromArena = Toolkit.inArena(e.getFrom());
		boolean nowInArena = Toolkit.inArena(p);

		if (nowInArena) {
			enterArena(p); // covers entering an arena and moving straight between two
		} else if (cameFromArena) {
			if (config.getBoolean("Arena.ClearInventoryOnLeave")) {
				p.getInventory().clear();
				p.getInventory().setArmorContents(null);
			}
			arena.removePlayer(p);
		}
	}

	private void enterArena(Player p) {
		if (config.getBoolean("Arena.ClearInventoryOnJoin")) {
			p.getInventory().clear();
			p.getInventory().setArmorContents(null);
		}

		arena.addPlayer(p, config.getBoolean("Arena.ToSpawnOnJoin"),
				config.getBoolean("Arena.GiveItemsOnJoin"));
	}

}
