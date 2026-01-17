package com.planetgallium.kitpvp.listener;

import com.cryptomorin.xseries.messages.Titles;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.util.CacheManager;
import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Resources;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

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
		if (Toolkit.inArena(e.getEntity())) {
			Player victim = e.getEntity();
			e.setDeathMessage("");

			if (config.getBoolean("Arena.PreventDeathDrops")) {
				e.getDrops().clear();
			}

			CacheManager.getPotionSwitcherUsers().remove(victim.getName());

			respawnPlayer(victim);
			setDeathMessage(victim);

			arena.getStats().addToStat("deaths", victim.getName(), 1);
			arena.getStats().removeExperience(victim.getName(),
					resources.getLevels().getInt("Levels.Options.Experience-Taken-On-Death"));

			if (config.getBoolean("Arena.DeathParticle")) {
				victim.getWorld().playEffect(victim.getLocation().add(0.0D, 1.0D, 0.0D), Effect.STEP_SOUND, 152);
			}

			Toolkit.runCommands(victim, config.getStringList("Death.Commands"), "%victim%", victim.getName());

			broadcast(victim.getWorld(),
					config.fetchString("Death.Sound.Sound"),
					config.getInt("Death.Sound.Pitch"));
		}
	}

	@EventHandler
	public void onRespawn(PlayerRespawnEvent e) {
		if (!Toolkit.inArena(e.getPlayer())) return;
		if (config.getBoolean("Arena.FancyDeath")) return;

		Player p = e.getPlayer();

		GameMode respawnGm = Toolkit.getConfiguredGameMode(
				plugin.getResources().getConfig(),
				"Respawn.RespawnGameMode",
				GameMode.ADVENTURE
		);

		new BukkitRunnable() {
			@Override
			public void run() {
				p.setGameMode(respawnGm);
				arena.toSpawn(p, p.getWorld().getName());
			}
		}.runTaskLater(plugin, 1L);
	}

	private void respawnPlayer(Player victim) {
		if (!victim.isOnline()) {
			return;
		}

		GameMode respawnGm = Toolkit.getConfiguredGameMode(
				plugin.getResources().getConfig(),
				"Respawn.RespawnGameMode",
				GameMode.ADVENTURE
		);

		if (config.getBoolean("Arena.FancyDeath")) {
			Location deathLocation = victim.getLocation();

			new BukkitRunnable() {
				@Override
				public void run() {
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
					if (time != 0) {
						Titles.sendTitle(victim, 0, 21, 0,
								config.fetchString("Death.Title.Title"),
								config.fetchString("Death.Title.Subtitle")
										.replace("%seconds%", String.valueOf(time)));
						Toolkit.playSoundToPlayer(victim, "UI_BUTTON_CLICK", 1);
						time--;
					} else {
						doClearInventoryOnRespawnIfEnabled(victim);

						arena.addPlayer(victim, true, config.getBoolean("Arena.GiveItemsOnRespawn"));
						victim.setGameMode(respawnGm);

						victim.sendMessage(config.fetchString("Death.Title.Message"));
						victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0));
						Toolkit.playSoundToPlayer(victim, "ENTITY_EXPERIENCE_ORB_PICKUP", 1);

						Toolkit.runCommands(victim, config.getStringList("Respawn.Commands"), "none", "none");

						cancel();
					}
				}
			}.runTaskTimer(plugin, 0L, 20L);

		} else {
			arena.removePlayer(victim);
			doClearInventoryOnRespawnIfEnabled(victim);

			new BukkitRunnable() {
				@Override
				public void run() {
					arena.addPlayer(victim, true, config.getBoolean("Arena.GiveItemsOnRespawn"));
					victim.setGameMode(respawnGm);

					Toolkit.runCommands(victim, config.getStringList("Respawn.Commands"),
							"none", "none");
				}
			}.runTaskLater(plugin, 1L);
		}
	}

	private void doClearInventoryOnRespawnIfEnabled(Player victim) {
		if (config.getBoolean("Arena.ClearInventoryOnRespawn")) {
			victim.getInventory().clear();
			victim.getInventory().setArmorContents(null);
			victim.setItemOnCursor(null);
			victim.getOpenInventory().getTopInventory().clear();
		}
	}

	private void setDeathMessage(Player victim) {
		if (victim.getLastDamageCause() == null) {
			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Unknown"));
			return;
		}

		EntityDamageEvent.DamageCause cause = victim.getLastDamageCause().getCause();

		if (cause == EntityDamageEvent.DamageCause.PROJECTILE && getShooter(victim.getLastDamageCause()).getType() == EntityType.PLAYER) {
			Player killer = (Player) getShooter(victim.getLastDamageCause());

			broadcast(victim.getWorld(), getDeathMessage(victim, killer, "Shot"));
			creditWithKill(victim, killer);

		} else if (victim.getKiller() != null) {
			Player killer = victim.getKiller();

			broadcast(victim.getWorld(), getDeathMessage(victim, killer, "Player"));
			creditWithKill(victim, killer);

		} else if (arena.getHitCache().get(victim.getName()) != null) {
			String killerName = arena.getHitCache().get(victim.getName());
			Player killer = Toolkit.getPlayer(victim.getWorld(), killerName);

			broadcast(victim.getWorld(), getDeathMessage(victim, killer, "Player"));
			creditWithKill(victim, killer);

		} else if ((cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) &&
				getExplodedEntity(victim.getLastDamageCause()).getType() == EntityType.PRIMED_TNT) {
			String bomberName = getExplodedEntity(victim.getLastDamageCause()).getCustomName();
			Player killer = Toolkit.getPlayer(victim.getWorld(), bomberName);

			broadcast(victim.getWorld(), getDeathMessage(victim, killer, "Player"));
			creditWithKill(victim, killer);

		} else if (cause == EntityDamageEvent.DamageCause.VOID) {
			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Void"));

		} else if (cause == EntityDamageEvent.DamageCause.FALL) {
			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Fall"));

		} else if (cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK || cause == EntityDamageEvent.DamageCause.LAVA) {
			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Fire"));

		} else if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Explosion"));

		} else {
			broadcast(victim.getWorld(), getDeathMessage(victim, null, "Unknown"));
		}
	}

	private Entity getShooter(EntityDamageEvent e) {
		EntityDamageByEntityEvent shotEvent = (EntityDamageByEntityEvent) e;
		Projectile damager = (Projectile) shotEvent.getDamager();
		return (Entity) damager.getShooter();
	}

	private Entity getExplodedEntity(EntityDamageEvent e) {
		EntityDamageByEntityEvent blownUpEvent = (EntityDamageByEntityEvent) e;
		return blownUpEvent.getDamager();
	}

	private void creditWithKill(Player victim, Player killer) {
		if (victim != null && killer != null) {
			if (!victim.getName().equals(killer.getName())) {
				arena.getStats().addToStat("kills", killer.getName(), 1);
				arena.getStats().addExperience(killer, resources.getLevels().getInt("Levels.Options.Experience-Given-On-Kill"));

				List<String> killCommands = config.getStringList("Kill.Commands");
				killCommands = Toolkit.replaceInList(killCommands, "%victim%", victim.getName());
				Toolkit.runCommands(killer, killCommands, "%killer%", killer.getName());

				if (resources.getScoreboard().getBoolean("Scoreboard.General.Enabled")) {
					new BukkitRunnable() {
						@Override
						public void run() {
							arena.updateScoreboards(killer, false);
						}
					}.runTaskLater(plugin, 20L);
				}
			}
		}
	}

	private String getDeathMessage(Player victim, Player killer, String type) {
		String deathMessage = config.fetchString("Death.Messages." + type);

		if (victim != null && killer != null) {
			if (victim.getName().equals(killer.getName())) {
				deathMessage = config.fetchString("Death.Messages.Suicide");
			}
		}

		if (killer != null) {
			deathMessage = deathMessage.replace("%killer%", killer.getName())
					.replace("%killer_health%", String.valueOf(Toolkit.round(killer.getHealth(), 2)));
		} else {
			deathMessage = config.fetchString("Death.Messages.Unknown");
		}

		if (victim != null) {
			deathMessage = deathMessage.replace("%victim%", victim.getName());
		}

		return deathMessage;
	}

	private void broadcast(World world, String message) {
		if (config.getBoolean("Death.Messages.Enabled")) {
			for (Player all : world.getPlayers()) {
				all.sendMessage(Toolkit.translate(message));
			}
		}
	}

	private void broadcast(World world, String soundName, int pitch) {
		if (config.getBoolean("Death.Sound.Enabled")) {
			for (Player all : world.getPlayers()) {
				Toolkit.playSoundToPlayer(all, soundName, pitch);
			}
		}
	}
}
