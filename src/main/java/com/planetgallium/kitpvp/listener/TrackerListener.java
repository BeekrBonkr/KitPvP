package com.planetgallium.kitpvp.listener;

import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.util.Resources;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.planetgallium.kitpvp.Game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the tracking compass pointed at the nearest player.
 *
 * There is at most one refresh task per player: scrolling onto a compass used to
 * start another one every time, and nothing ever cancelled the old ones.
 */
public class TrackerListener implements Listener {

	private final Game plugin;
	private final Arena arena;
	private final Resources resources;

	private final Map<UUID, BukkitTask> trackingTasks;

	public TrackerListener(Game plugin) {
		this.plugin = plugin;
		this.arena = plugin.getArena();
		this.resources = plugin.getResources();
		this.trackingTasks = new HashMap<>();
	}

	@EventHandler
	public void onCompassHeld(PlayerItemHeldEvent e) {
		Player p = e.getPlayer();

		if (!Toolkit.inArena(p)) {
			return;
		}

		ItemStack itemHeld = p.getInventory().getItem(e.getNewSlot());

		if (itemHeld == null || !Toolkit.hasMatchingMaterial(itemHeld, "COMPASS")) {
			return;
		}

		startTracking(p);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		stopTracking(e.getPlayer().getUniqueId());
	}

	private void startTracking(Player p) {
		stopTracking(p.getUniqueId()); // never leave a second timer running for the same player

		BukkitTask task = new BukkitRunnable() {
			@Override
			public void run() {
				if (!p.isOnline() || !arena.getKits().playerHasKit(p.getName()) || !Toolkit.inArena(p)) {
					stopTracking(p.getUniqueId());
					return;
				}

				if (resources.getConfig().getBoolean("PlayerTracker.RefreshOnlyWhenHeld") &&
						!Toolkit.eitherHandHasMaterial(p, "COMPASS")) {
					stopTracking(p.getUniqueId());
					return;
				}

				ItemStack compass = findHeldCompass(p);
				if (compass == null) {
					stopTracking(p.getUniqueId());
					return;
				}

				Toolkit.Nearest nearest = Toolkit.getNearestPlayer(p,
						resources.getConfig().getInt("PlayerTracker.TrackBelowY"));

				updateTrackingCompass(p, compass, nearest);
			}
		}.runTaskTimer(plugin, 0L, 20L);

		trackingTasks.put(p.getUniqueId(), task);
	}

	private void stopTracking(UUID playerId) {
		BukkitTask existing = trackingTasks.remove(playerId);
		if (existing != null) {
			existing.cancel();
		}
	}

	public void stopAll() {
		for (BukkitTask task : trackingTasks.values()) {
			task.cancel();
		}
		trackingTasks.clear();
	}

	/** The compass the player is actually holding now, rather than the one they held on the event. */
	private ItemStack findHeldCompass(Player p) {
		ItemStack mainHand = p.getInventory().getItem(p.getInventory().getHeldItemSlot());
		if (mainHand != null && Toolkit.hasMatchingMaterial(mainHand, "COMPASS")) {
			return mainHand;
		}

		if (Toolkit.versionToNumber() >= 19) {
			ItemStack offHand = p.getInventory().getItemInOffHand();
			if (Toolkit.hasMatchingMaterial(offHand, "COMPASS")) {
				return offHand;
			}
		}
		return null;
	}

	private void updateTrackingCompass(Player player, ItemStack compass, Toolkit.Nearest nearest) {
		ItemMeta compassMeta = compass.getItemMeta();

		if (compassMeta == null) {
			return;
		}

		if (nearest != null && nearest.getPlayer().isOnline()) {
			compassMeta.setDisplayName(resources.getConfig().fetchString("PlayerTracker.Message")
											   .replace("%nearestplayer%", nearest.getPlayer().getName())
											   .replace("%distance%",
													   String.valueOf(Toolkit.round(nearest.getDistance(), 1))));
			player.setCompassTarget(nearest.getPlayer().getLocation());
		} else {
			compassMeta.setDisplayName(resources.getConfig().fetchString("PlayerTracker.NoneOnline"));
		}

		compass.setItemMeta(compassMeta);
	}

}
