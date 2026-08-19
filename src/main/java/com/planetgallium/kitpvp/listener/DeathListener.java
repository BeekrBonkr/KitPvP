package com.planetgallium.kitpvp.listener;

import com.cryptomorin.xseries.messages.Titles;
import com.planetgallium.kitpvp.game.CombatTagger;
import com.planetgallium.kitpvp.util.*;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.game.Arena;

public class DeathListener implements Listener {

	private final Game plugin;
	private final Arena arena;
	private final Resources resources;
	private final Resource config;

	public DeathListener(Game plugin) {
		this.plugin = plugin;
		this.arena = plugin.getArena();
		this.resources = plugin.getResources();
		this.config = resources.getConfig();
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent e) {
		Player victim = e.getEntity();

		if (!Toolkit.inArena(victim)) {
			return;
		}

		e.setDeathMessage(null);

		if (config.getBoolean("Arena.PreventDeathDrops")) {
			e.getDrops().clear();
		}

		// Who gets the kill has to be worked out before anything is reset: the
		// combat tag that answers "who knocked them into the void" is cleared as
		// part of taking the player out of the arena.
		Death death = resolveDeath(victim);

		broadcast(victim.getWorld(), getDeathMessage(victim, death.killer, death.messageType));

		arena.getStats().addToStat("deaths", victim.getName(), 1);
		arena.getStats().removeExperience(victim.getName(),
				resources.getLevels().getInt("Levels.Options.Experience-Taken-On-Death"));
		arena.getKillStreaks().endStreak(victim);
		arena.creditKill(death.killer, victim);

		CacheManager.getPotionSwitcherUsers().remove(victim.getName());
		plugin.getCombatTagger().clear(victim);

		if (config.getBoolean("Arena.DeathParticles")) {
			victim.getWorld().playEffect(victim.getLocation().add(0.0D, 1.0D, 0.0D), Effect.STEP_SOUND, 152);
		}

		Toolkit.runCommands(victim, config.getStringList("Death.Commands"), "%victim%", victim.getName());

		broadcast(victim.getWorld(),
				config.fetchString("Death.Sound.Sound"),
				config.getInt("Death.Sound.Pitch"));

		respawnPlayer(victim);
	}

	@EventHandler
	public void onRespawn(PlayerRespawnEvent e) {
		Player p = e.getPlayer();

		if (!Toolkit.inArena(p) || config.getBoolean("Arena.FancyDeath")) {
			return; // the fancy path does its own respawning
		}

		// Setting the respawn location here is what avoids the player being
		// teleported a second time a tick after they land at the world spawn.
		Location arenaSpawn = arena.getRandomSpawnLocation(p.getWorld().getName());
		if (arenaSpawn != null) {
			e.setRespawnLocation(arenaSpawn);
		}

		new BukkitRunnable() {
			@Override
			public void run() {
				if (!p.isOnline()) {
					return;
				}

				doClearInventoryOnRespawnIfEnabled(p);
				arena.addPlayer(p, arenaSpawn == null, config.getBoolean("Arena.GiveItemsOnRespawn"));
				Toolkit.runCommands(p, config.getStringList("Respawn.Commands"), "none", "none");
			}
		}.runTaskLater(plugin, 1L);
	}

	private void respawnPlayer(Player victim) {
		if (!victim.isOnline()) {
			return;
		}

		if (!config.getBoolean("Arena.FancyDeath")) {
			arena.removePlayer(victim);
			return; // onRespawn takes it from here
		}

		Location deathLocation = victim.getLocation();

		new BukkitRunnable() {
			@Override
			public void run() {
				if (!victim.isOnline()) {
					return;
				}
				victim.spigot().respawn();
				victim.teleport(deathLocation);
				victim.setGameMode(GameMode.SPECTATOR);
			}
		}.runTaskLater(plugin, 1L);

		arena.removePlayer(victim);

		new BukkitRunnable() {
			int time = config.getInt("Death.Title.Time");

			@Override
			public void run() {
				// a player who quits during the countdown must not be teleported,
				// re-equipped and left running a task nobody can see
				if (!victim.isOnline()) {
					cancel();
					return;
				}

				if (time > 0) {
					Titles.sendTitle(victim, 0, 21, 0,
							config.fetchString("Death.Title.Title"),
							config.fetchString("Death.Title.Subtitle")
									.replace("%seconds%", String.valueOf(time)));
					Toolkit.playSoundToPlayer(victim, "UI_BUTTON_CLICK", 1);
					time--;
				} else {
					doClearInventoryOnRespawnIfEnabled(victim);

					arena.addPlayer(victim, true, config.getBoolean("Arena.GiveItemsOnRespawn"));

					victim.sendMessage(config.fetchString("Death.Title.Message"));
					victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0));
					Toolkit.playSoundToPlayer(victim, "ENTITY_EXPERIENCE_ORB_PICKUP", 1);

					Toolkit.runCommands(victim, config.getStringList("Respawn.Commands"), "none", "none");

					cancel();
				}
			}
		}.runTaskTimer(plugin, 0L, 20L);
	}

	private void doClearInventoryOnRespawnIfEnabled(Player victim) {
		if (config.getBoolean("Arena.ClearInventoryOnRespawn")) {
			victim.getInventory().clear();
			victim.getInventory().setArmorContents(null);
			victim.setItemOnCursor(null);
			victim.getOpenInventory().getTopInventory().clear();
		}
	}

	/**
	 * Works out who killed the player and which configured message describes it.
	 * Every source is tried in turn: the server's own killer, the entity behind
	 * the last damage (which covers arrows, thrown tridents and named TNT), and
	 * finally the combat tag, which is what credits a player who was knocked off
	 * a ledge rather than hit directly.
	 */
	private Death resolveDeath(Player victim) {
		EntityDamageEvent lastDamage = victim.getLastDamageCause();
		DamageCause cause = lastDamage == null ? null : lastDamage.getCause();

		Entity damager = lastDamage instanceof EntityDamageByEntityEvent ?
				((EntityDamageByEntityEvent) lastDamage).getDamager() : null;

		Player killer = victim.getKiller();
		if (killer == null && damager != null) {
			killer = CombatTagger.resolveAttacker(damager);
		}
		if (killer == null) {
			killer = plugin.getCombatTagger().getAttackerOf(victim);
		}

		if (killer != null) {
			if (killer.getName().equals(victim.getName())) {
				return new Death(null, "Suicide");
			}
			return new Death(killer, damager instanceof Projectile ? "Shot" : "Player");
		}

		if (cause == DamageCause.VOID) {
			return new Death(null, "Void");
		} else if (cause == DamageCause.FALL) {
			return new Death(null, "Fall");
		} else if (cause == DamageCause.FIRE || cause == DamageCause.FIRE_TICK || cause == DamageCause.LAVA) {
			return new Death(null, "Fire");
		} else if (cause == DamageCause.BLOCK_EXPLOSION || cause == DamageCause.ENTITY_EXPLOSION) {
			return new Death(null, "Explosion");
		}

		return new Death(null, "Unknown");
	}

	/**
	 * The configured message for this kind of death. A death with no killer keeps
	 * its own message — Void, Fall, Fire and Explosion used to be replaced by the
	 * Unknown message before they could ever be shown.
	 */
	private String getDeathMessage(Player victim, Player killer, String type) {
		String deathMessage = config.fetchStringOrNull("Death.Messages." + type);

		if (deathMessage == null) {
			deathMessage = config.fetchStringOrNull("Death.Messages.Unknown");
		}
		if (deathMessage == null) {
			return "";
		}

		deathMessage = deathMessage.replace("%victim%", victim.getName());

		if (killer != null) {
			deathMessage = deathMessage.replace("%killer%", killer.getName())
					.replace("%killer_health%", String.valueOf(Toolkit.round(killer.getHealth(), 2)));
		}

		return deathMessage;
	}

	private void broadcast(World world, String message) {
		if (message.isEmpty() || !config.getBoolean("Death.Messages.Enabled")) {
			return;
		}

		for (Player all : world.getPlayers()) {
			all.sendMessage(Toolkit.translate(message));
		}
	}

	private void broadcast(World world, String soundName, int pitch) {
		if (config.getBoolean("Death.Sound.Enabled")) {
			for (Player all : world.getPlayers()) {
				Toolkit.playSoundToPlayer(all, soundName, pitch);
			}
		}
	}

	/** Who killed the player, and which Death.Messages entry describes it. */
	private static class Death {

		private final Player killer;
		private final String messageType;

		Death(Player killer, String messageType) {
			this.killer = killer;
			this.messageType = messageType;
		}

	}

}
