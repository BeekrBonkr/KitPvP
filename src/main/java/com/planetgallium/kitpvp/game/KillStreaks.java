package com.planetgallium.kitpvp.game;

import java.util.HashMap;
import java.util.Map;

import com.cryptomorin.xseries.messages.Titles;
import com.planetgallium.kitpvp.util.*;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Kill streaks are driven by Arena.creditKill rather than by their own death
 * listener, so a streak counts exactly the kills that the stats count — including
 * kills by TNT, projectiles and abilities, which the old listener missed.
 */
public class KillStreaks implements Listener {

	private final Resources resources;
	private final Resource killConfig;
	private final Map<String, Integer> kills;

	public KillStreaks(Resources resources) {
		this.resources = resources;
		this.killConfig = resources.getKillStreaks();
		this.kills = new HashMap<>();
	}

	/** One more kill for this player, announcing whatever the new streak has earned. */
	public void addKill(Player killer) {
		kills.put(killer.getName(), getStreak(killer.getName()) + 1);
		runStreakCase("KillStreaks", killer);
	}

	/** Ends a streak, announcing the loss first while the number is still meaningful. */
	public void endStreak(Player player) {
		if (getStreak(player.getName()) > 0) {
			runStreakCase("EndStreaks", player);
		}
		kills.put(player.getName(), 0);
	}

	@EventHandler
	public void createStreak(PlayerJoinEvent e) {
		Player p = e.getPlayer();

		if (!kills.containsKey(p.getName())) {
			kills.put(p.getName(), 0);
		}
	}

	@EventHandler
	public void removeStreak(PlayerQuitEvent e) {
		// the entry is dropped entirely rather than zeroed, so the map does not grow
		// by one dead entry for every player the server has ever seen
		kills.remove(e.getPlayer().getName());
	}

	public void runStreakCase(String streakType, Player p) {
		String username = p.getName();
		int streakNumber = getStreak(username);
		World world = p.getWorld();

		String pathPrefix = streakType + "." + streakNumber;

		if (killConfig.contains(pathPrefix)) {

			// TITLE
			if (killConfig.contains(pathPrefix + ".Title")) {
				String title = killConfig.fetchString(pathPrefix + ".Title.Title")
						.replace("%player%", username)
						.replace("%streak%", String.valueOf(streakNumber));
				String subtitle = killConfig.fetchString(pathPrefix + ".Title.Subtitle")
						.replace("%player%", username)
						.replace("%streak%", String.valueOf(streakNumber));

				for (Player local : world.getPlayers()) {
					Titles.sendTitle(local, 20, 60, 20, title, subtitle);
				}
			}

			// SOUND
			if (killConfig.contains(pathPrefix + ".Sound")) {
				Sound sound = Toolkit.safeSound(killConfig.fetchString(pathPrefix + ".Sound.Sound"));
				int pitch = killConfig.getInt(pathPrefix + ".Sound.Pitch");
				float clampedPitch = Math.max(0.5f, Math.min(2.0f, pitch <= 0 ? 1.0f : (float) pitch));

				for (Player local : world.getPlayers()) {
					local.playSound(local.getLocation(), sound, 1, clampedPitch);
				}
			}

			// MESSAGE
			if (killConfig.contains(pathPrefix + ".Message")) {
				String message = killConfig.fetchString(pathPrefix + ".Message.Message")
						.replace("%streak%", String.valueOf(streakNumber))
						.replace("%player%", username);

				for (Player local : world.getPlayers()) {
					local.sendMessage(message);
				}
			}

			// COMMANDS
			if (killConfig.contains(pathPrefix + ".Commands")) {
				Toolkit.runCommands(p, killConfig.getStringList(pathPrefix + ".Commands"),
						"none", "none");
			}

		}

	}

	public int getStreak(String username) {
		Integer streak = kills.get(username);
		return streak == null ? 0 : streak;
	}

	public void resetStreak(Player p) {
		endStreak(p);
	}

	public void setStreak(Player p, int streak) {
		kills.put(p.getName(), streak);
	}

	public void forget(String username) {
		kills.remove(username);
	}

}
