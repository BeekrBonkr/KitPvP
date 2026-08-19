package com.planetgallium.kitpvp.game;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps track of who is currently in a fight and who they are fighting.
 *
 * This replaces the old Arena hit cache, which had no expiry and was cleared
 * before it could be read. Two jobs: remember the most recent attacker so a
 * knock-into-the-void still credits a kill, and stop a losing player escaping
 * a fight through /kp clear, a kit switch, a teleport, or the quit button.
 */
public class CombatTagger implements Listener {

	private final Game plugin;
	private final Map<UUID, Tag> tags;

	private BukkitTask expiryTask;

	public CombatTagger(Game plugin) {
		this.plugin = plugin;
		this.tags = new HashMap<>();
	}

	public void start() {
		stop();
		this.expiryTask = new BukkitRunnable() {
			@Override
			public void run() {
				expireFinishedTags();
			}
		}.runTaskTimer(plugin, 20L, 20L);
	}

	public void stop() {
		if (expiryTask != null) {
			expiryTask.cancel();
			expiryTask = null;
		}
	}

	private Resource config() {
		return plugin.getResources().getConfig();
	}

	private Resource messages() {
		return plugin.getResources().getMessages();
	}

	public boolean isEnabled() {
		return config().getBoolean("Combat.Tag.Enabled");
	}

	public int getDurationSeconds() {
		int duration = config().getInt("Combat.Tag.Duration");
		return duration > 0 ? duration : 15;
	}

	/**
	 * Puts both players in combat with each other. Called for every player-on-player
	 * hit, including projectiles and abilities that deal damage directly.
	 */
	public void tag(Player victim, Player attacker) {
		if (!isEnabled() || victim == null || attacker == null || victim.equals(attacker)) {
			return;
		}

		long expiresAt = System.currentTimeMillis() + (getDurationSeconds() * 1000L);

		applyTag(victim, attacker, expiresAt);
		applyTag(attacker, victim, expiresAt);
	}

	private void applyTag(Player player, Player opponent, long expiresAt) {
		Tag existing = tags.get(player.getUniqueId());
		boolean wasAlreadyTagged = existing != null && !existing.hasExpired();

		tags.put(player.getUniqueId(), new Tag(opponent.getUniqueId(), opponent.getName(), expiresAt));

		if (!wasAlreadyTagged) {
			String message = messages().fetchString("Messages.Combat.Tagged")
					.replace("%seconds%", String.valueOf(getDurationSeconds()));
			if (!message.isEmpty()) {
				player.sendMessage(message);
			}
		}
	}

	public boolean isTagged(Player player) {
		if (player == null) {
			return false;
		}
		Tag tag = tags.get(player.getUniqueId());
		return tag != null && !tag.hasExpired();
	}

	public int getSecondsRemaining(Player player) {
		Tag tag = tags.get(player.getUniqueId());
		if (tag == null || tag.hasExpired()) {
			return 0;
		}
		return (int) Math.max(1, (tag.getExpiresAt() - System.currentTimeMillis() + 999) / 1000);
	}

	/**
	 * The player who most recently damaged this one, while the tag is still live.
	 * Null once the tag has expired, so an old hit cannot credit an unrelated death.
	 */
	public Player getAttackerOf(Player victim) {
		Tag tag = tags.get(victim.getUniqueId());
		if (tag == null || tag.hasExpired()) {
			return null;
		}

		Player attacker = Bukkit.getPlayer(tag.getOpponentId());
		if (attacker == null || attacker.equals(victim)) {
			return null;
		}
		return attacker;
	}

	/** The name of the most recent attacker, even if they have since gone offline. */
	public String getAttackerNameOf(Player victim) {
		Tag tag = tags.get(victim.getUniqueId());
		if (tag == null || tag.hasExpired()) {
			return null;
		}
		return tag.getOpponentName();
	}

	public void clear(Player player) {
		tags.remove(player.getUniqueId());
	}

	public void clearAll() {
		tags.clear();
	}

	/**
	 * Guard for anything that would let a player leave a fight for free. Sends the
	 * explanation itself and returns true when the action must not go ahead.
	 */
	public boolean denyIfTagged(Player player) {
		if (!isTagged(player)) {
			return false;
		}

		String message = messages().fetchString("Messages.Combat.Blocked")
				.replace("%seconds%", String.valueOf(getSecondsRemaining(player)));
		if (!message.isEmpty()) {
			player.sendMessage(message);
		}
		Toolkit.playSoundToPlayer(player, "ENTITY_VILLAGER_NO", 1);
		return true;
	}

	private void expireFinishedTags() {
		tags.entrySet().removeIf(entry -> {
			if (!entry.getValue().hasExpired()) {
				return false;
			}

			Player player = Bukkit.getPlayer(entry.getKey());
			if (player != null && player.isOnline()) {
				String message = messages().fetchString("Messages.Combat.Expired");
				if (!message.isEmpty()) {
					player.sendMessage(message);
				}
			}
			return true;
		});
	}

	@EventHandler(ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent e) {
		if (!(e.getEntity() instanceof Player) || !Toolkit.inArena(e.getEntity())) {
			return;
		}

		Player victim = (Player) e.getEntity();
		Player attacker = resolveAttacker(e.getDamager());

		if (attacker != null) {
			tag(victim, attacker);
		}
	}

	/**
	 * Quitting mid-fight is treated as dying: the death is recorded, the streak
	 * ends, and whoever was fighting them is credited with the kill.
	 */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onQuit(PlayerQuitEvent e) {
		Player player = e.getPlayer();

		if (!isEnabled() || !isTagged(player) || !config().getBoolean("Combat.Tag.PunishOnQuit")) {
			clear(player);
			return;
		}

		Arena arena = plugin.getArena();
		String attackerName = getAttackerNameOf(player);
		Player attacker = getAttackerOf(player);

		arena.getStats().addToStat("deaths", player.getName(), 1);
		arena.getStats().removeExperience(player.getName(),
				plugin.getResources().getLevels().getInt("Levels.Options.Experience-Taken-On-Death"));
		arena.getKillStreaks().resetStreak(player);

		if (attacker != null) {
			arena.creditKill(attacker, player);
		}

		String broadcast = messages().fetchString("Messages.Combat.Logged")
				.replace("%player%", player.getName())
				.replace("%killer%", attackerName == null ? "" : attackerName);
		if (!broadcast.isEmpty()) {
			for (Player all : player.getWorld().getPlayers()) {
				all.sendMessage(broadcast);
			}
		}

		clear(player);
	}

	@EventHandler(ignoreCancelled = true)
	public void onCommand(PlayerCommandPreprocessEvent e) {
		Player player = e.getPlayer();

		if (!isTagged(player) || player.hasPermission("kp.combattag.bypass")) {
			return;
		}

		String typed = e.getMessage().toLowerCase();
		if (typed.startsWith("/")) {
			typed = typed.substring(1);
		}

		List<String> blocked = config().getStringList("Combat.Tag.BlockedCommands");
		for (String blockedCommand : blocked) {
			String candidate = blockedCommand.toLowerCase().trim();
			if (candidate.isEmpty()) {
				continue;
			}
			if (typed.equals(candidate) || typed.startsWith(candidate + " ")) {
				e.setCancelled(true);
				denyIfTagged(player);
				return;
			}
		}
	}

	/**
	 * The player behind a damage source: the attacker themselves, whoever fired the
	 * projectile, or the player whose name was stamped on a primed TNT by the
	 * Bomber ability. Null for environmental damage and for mobs.
	 */
	public static Player resolveAttacker(Entity damager) {
		if (damager instanceof Player) {
			return (Player) damager;
		}

		if (damager instanceof Projectile) {
			ProjectileSource shooter = ((Projectile) damager).getShooter();
			return shooter instanceof Player ? (Player) shooter : null;
		}

		if (damager != null && damager.getType() == EntityType.PRIMED_TNT) {
			String stampedName = damager.getCustomName();
			return stampedName == null ? null : Bukkit.getPlayerExact(stampedName);
		}

		return null;
	}

	private static class Tag {

		private final UUID opponentId;
		private final String opponentName;
		private final long expiresAt;

		Tag(UUID opponentId, String opponentName, long expiresAt) {
			this.opponentId = opponentId;
			this.opponentName = opponentName;
			this.expiresAt = expiresAt;
		}

		boolean hasExpired() { return System.currentTimeMillis() >= expiresAt; }

		UUID getOpponentId() { return opponentId; }

		String getOpponentName() { return opponentName; }

		long getExpiresAt() { return expiresAt; }

	}

}
