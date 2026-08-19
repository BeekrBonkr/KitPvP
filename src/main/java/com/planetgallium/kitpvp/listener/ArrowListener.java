package com.planetgallium.kitpvp.listener;

import com.planetgallium.kitpvp.util.Resource;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.util.Toolkit;

public class ArrowListener implements Listener {

	private final Game plugin;
	private final Resource config;

	public ArrowListener(Game plugin) {
		this.plugin = plugin;
		this.config = plugin.getResources().getConfig();
	}

	@EventHandler(ignoreCancelled = true)
	public void onArrowHit(EntityDamageByEntityEvent e) {
		if (Toolkit.inArena(e.getEntity()) &&
				e.getEntity() instanceof Player && e.getDamager() instanceof Arrow) {
			Player damagedPlayer = (Player) e.getEntity();
			Arrow arrow = (Arrow) e.getDamager();

			if (arrow.getShooter() instanceof Player) {
				Player shooter = (Player) arrow.getShooter();

				// make sure damaged player isn't shooter (self-hit)
				if (!damagedPlayer.getName().equals(shooter.getName())) {
					doArrowHitMessageIfEnabled(shooter, damagedPlayer);
					doArrowReturnIfEnabled(shooter, damagedPlayer);
				}
			}
		}
	}

	private void doArrowHitMessageIfEnabled(Player shooter, Player damagedPlayer) {
		if (!config.getBoolean("Combat.ArrowHit.Enabled") || !shooter.hasPermission("kp.arrowmessage")) {
			return;
		}

		// a tick later, so the reported health is the health after the arrow landed
		new BukkitRunnable() {
			@Override
			public void run() {
				if (!shooter.isOnline() || !damagedPlayer.isOnline()) {
					return;
				}

				double health = Toolkit.round(damagedPlayer.getHealth(), 1);

				shooter.sendMessage(config.fetchString("Combat.ArrowHit.Message")
						.replace("%player%", damagedPlayer.getName())
						.replace("%health%", String.valueOf(health))
						.replace("%max_health%", String.valueOf(Toolkit.round(Toolkit.getMaxHealth(damagedPlayer), 1))));
			}
		}.runTaskLater(plugin, 2L);
	}

	private void doArrowReturnIfEnabled(Player shooter, Player damagedPlayer) {
		if (!config.getBoolean("Combat.ArrowReturn.Enabled") || !shooter.hasPermission("kp.arrowreturn")) {
			return;
		}

		int count = Math.max(1, config.getInt("Combat.ArrowReturn.Count"));
		ItemStack arrowToAdd = Toolkit.safeItemStack("ARROW");
		arrowToAdd.setAmount(count);

		// an existing part-full stack has room without needing a free slot
		for (int i = 0; i < 36; i++) {
			ItemStack item = shooter.getInventory().getItem(i);
			if (item != null && item.getType() == arrowToAdd.getType() &&
					item.getAmount() + count <= item.getType().getMaxStackSize()) {
				shooter.getInventory().addItem(arrowToAdd);
				return;
			}
		}

		// keep a slot empty for each kit trident currently out of the inventory
		int reservedSlotCount = Toolkit.countMissingKitTridents(
				plugin.getArena().getKits().getKitOfPlayer(shooter.getName()), shooter);

		if (Toolkit.countEmptyMainSlots(shooter) <= reservedSlotCount) {
			shooter.sendMessage(config.fetchString("Combat.ArrowReturn.NoSpace"));
		} else {
			shooter.getInventory().addItem(arrowToAdd);
		}
	}

}
