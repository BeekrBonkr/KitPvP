package com.planetgallium.kitpvp.listener;

import com.cryptomorin.xseries.XMaterial;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.util.Resource;
import org.bukkit.GameMode;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import com.planetgallium.kitpvp.util.Toolkit;

public class ArenaListener implements Listener {

	private final Resource config;

	public ArenaListener(Game plugin) {
		this.config = plugin.getResources().getConfig();
	}

	@EventHandler
	public void onBreak(BlockBreakEvent e) {
		Player p = e.getPlayer();

		if (Toolkit.inArena(p) && config.getBoolean("Arena.PreventBlockBreaking")) {
			e.setCancelled(!p.hasPermission("kp.arena.blockbreaking"));
		}
	}

	@EventHandler
	public void onPlace(BlockPlaceEvent e) {
		Player p = e.getPlayer();

		if (Toolkit.inArena(p) && config.getBoolean("Arena.PreventBlockPlacing")) {
			e.setCancelled(!p.hasPermission("kp.arena.blockplacing"));
		}

	}

	@EventHandler
	public void onItemDamage(PlayerItemDamageEvent e) {
		if (Toolkit.inArena(e.getPlayer()) && config.getBoolean("Arena.PreventItemDurabilityDamage")) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onDrop(PlayerDropItemEvent e) {
		Player p = e.getPlayer();

		if (Toolkit.inArena(p) && config.getBoolean("Arena.PreventItemDropping")) {
			e.setCancelled(!p.hasPermission("kp.arena.itemdropping"));
		}
	}

	@EventHandler
	public void onHunger(FoodLevelChangeEvent e) {
		if (!(e.getEntity() instanceof Player)) {
			return;
		}

		Player p = (Player) e.getEntity();

		if (Toolkit.inArena(p) && config.getBoolean("Arena.PreventHunger")) {
			e.setCancelled(true);
		}
	}

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
    	if (Toolkit.inArena(e.getEntity()) && config.getBoolean("Arena.PreventBlockBreaking")) {
			// TNT: enable explosion animation; wind charges (1.21+, compared by
			// name so this compiles against older APIs): keep their knockback
			// burst working. Neither is allowed to break blocks.
			String entityTypeName = e.getEntityType().name();
    		if (e.getEntityType() == EntityType.PRIMED_TNT ||
					entityTypeName.equals("WIND_CHARGE") || entityTypeName.equals("BREEZE_WIND_CHARGE")) {
    			e.blockList().clear();
    			e.setCancelled(false);
    			return;
			}
			e.setCancelled(true);
		}
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
		if (!Toolkit.inArena(e.getEntity())) {
			return;
		}

		if (e.getDamager().getType() == EntityType.PRIMED_TNT) {
			if (e.getEntity() instanceof ArmorStand || !(e.getEntity() instanceof LivingEntity)) {
				// for preventing breakage of paintings, item frames, etc.
				e.setCancelled(true);
			}
		}
	}

	@EventHandler
	public void onWeatherChange(WeatherChangeEvent e) {
		if (Toolkit.inArena(e.getWorld()) && config.getBoolean("Arena.KeepWeatherAtSunny")) {
			if (e.toWeatherState()) {
				e.setCancelled(true);
				e.getWorld().setStorm(false);
				e.getWorld().setThundering(false);
				e.getWorld().setWeatherDuration(0);
			}
		}
	}

	@EventHandler
	public void onExplosion(EntityDamageEvent e) {
		if (!(e.getEntity() instanceof Player)) {
			return;
		}

		Player damagedPlayer = (Player) e.getEntity();

		if (!Toolkit.inArena(damagedPlayer)) {
			return;
		}

		if (e.getCause() == DamageCause.FALL) {
			if (config.getBoolean("Arena.PreventFallDamage")) {
				e.setCancelled(true);
				// only canceling if preventing fall damage is enabled, this allows for WorldGuard to step in
			}
		} else if (damagedPlayer.getGameMode() == GameMode.SPECTATOR) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		Player p = e.getPlayer();

		if (Toolkit.inArena(p) && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
			if (e.getClickedBlock() != null && e.getClickedBlock().getType() == XMaterial.CHEST.parseMaterial()) {
				if (config.getBoolean("Arena.PreventChestOpen")) {
					e.setCancelled(true);
				}
			}
		}
	}

	@EventHandler
	public void onHangingEntityBreakByTNT(HangingBreakEvent e) {
		if (Toolkit.inArena(e.getEntity())) {
			if (e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION) {
				e.setCancelled(true);
			}
		}
	}

}
