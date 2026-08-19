package com.planetgallium.kitpvp.listener;

import com.planetgallium.kitpvp.util.CacheManager;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.game.Arena;

public class LeaveListener implements Listener {

	private final Arena arena;

	public LeaveListener(Game plugin) {
		this.arena = plugin.getArena();
	}

	// runs after the combat tagger has had its say about players who quit mid-fight
	@EventHandler(priority = EventPriority.NORMAL)
	public void onLeave(PlayerQuitEvent e) {
		Player p = e.getPlayer();

		if (Toolkit.inArena(p)) {
			arena.deletePlayer(p);
		}

		// caches keyed by name would otherwise keep an entry for every player
		// the server has ever seen
		CacheManager.forgetPlayer(p.getName());
	}

}
