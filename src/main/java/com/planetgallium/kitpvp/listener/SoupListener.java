package com.planetgallium.kitpvp.listener;

import com.planetgallium.kitpvp.api.Kit;
import com.planetgallium.kitpvp.util.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import com.planetgallium.kitpvp.Game;

public class SoupListener implements Listener {

	private final Game plugin;
	private final Resource config;

	public SoupListener(Game plugin) {
		this.plugin = plugin;
		this.config = plugin.getResources().getConfig();
	}
	
	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent e) {
		Player victim = e.getEntity();

		if (!Toolkit.inArena(victim)) {
			return;
		}
			
		if (victim.getKiller() != null && config.getBoolean("Kill.SoupReward.Enabled")) {
			Player killer = victim.getKiller();

			if (!killer.hasPermission("kp.soupreturn")) {
				return;
			}

			new BukkitRunnable() {
				@Override
				public void run() {
					if (killer.isOnline()) {
						insertSoupRewardToInventory(killer);
					}
				}
			}.runTaskLater(plugin, config.getInt("Kill.SoupReward.Delay") * 20L);
		}
	}

	private void insertSoupRewardToInventory(Player killer) {
		Kit kit = plugin.getArena().getKits().getKitOfPlayer(killer.getName());
		int reservedSlotCount = Toolkit.countMissingKitTridents(kit, killer);

		for (int i = config.getInt("Kill.SoupReward.Amount"); i > 0; i--) {
			int slot = findEmptySlotKeepingReserve(killer, reservedSlotCount);
			if (slot == -1) {
				killer.sendMessage(config.fetchString("Kill.SoupReward.NoSpace")
						.replace("%amount%", String.valueOf(i)));
				break;
			} else {
				killer.getInventory().setItem(slot, buildSoup());
			}
		}
	}

	/**
	 * First empty main-inventory slot, or -1 if filling one would leave fewer
	 * than reservedSlotCount slots empty. A kit trident that is out of the
	 * inventory returns to the slot it was thrown from — not necessarily the
	 * kit's configured slot — so the reserve is a count, not specific slots.
	 */
	private int findEmptySlotKeepingReserve(Player p, int reservedSlotCount) {
		if (Toolkit.countEmptyMainSlots(p) <= reservedSlotCount) {
			return -1;
		}
		for (int i = 0; i < 36; i++) {
			if (Toolkit.isEmpty(p.getInventory().getItem(i))) {
				return i;
			}
		}
		return -1;
	}
	
	@EventHandler
	public void onSoupUse(PlayerInteractEvent e) {
		if (!config.getBoolean("Soups.Enabled")) {
			return;
		}

		Player p = e.getPlayer();

		if (Toolkit.inArena(p) &&
				(e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
			if (Toolkit.hasMatchingMaterial(Toolkit.getHandItemForInteraction(e), "MUSHROOM_STEW")) {
				e.setCancelled(true);

				if (p.getHealth() < 20.0) {
					int soupBoost = plugin.getConfig().getInt("Soups.RegenAmount");

					p.setHealth(Math.min(p.getHealth() + (double) soupBoost, 20.0));

					Toolkit.playSoundToPlayer(p, config.fetchString("Soups.Sound"),
							config.getInt("Soups.Pitch"));
					Toolkit.setHandItemForInteraction(e,config.getBoolean("Soups.RemoveAfterUse") ?
							Toolkit.safeItemStack("AIR") : Toolkit.safeItemStack("BOWL"));
				}
			}
		}
	}

	private ItemStack buildSoup() {
		ItemStack soup = Toolkit.safeItemStack("MUSHROOM_STEW");
		ItemMeta soupMeta = soup.getItemMeta();

		soupMeta.setDisplayName(config.fetchString("Soups.Name"));
		soupMeta.setLore(config.getStringList("Soups.Lore"));

		soup.setItemMeta(soupMeta);
		return soup;
	}
	
}
