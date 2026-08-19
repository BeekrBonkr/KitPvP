package com.planetgallium.kitpvp.game;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.api.Ability;
import com.planetgallium.kitpvp.api.Kit;
import com.planetgallium.kitpvp.util.CacheManager;
import com.planetgallium.kitpvp.util.Cooldown;
import com.planetgallium.kitpvp.util.PlayerData;
import org.bukkit.entity.Player;

public class Cooldowns {

	private final Stats stats;
	private final Infobase database;

	public Cooldowns(Game plugin, Arena arena) {
		this.stats = arena.getStats();
		this.database = plugin.getDatabase();
	}

	public void setAbilityCooldown(String playerName, String abilityName) {
		CacheManager.getPlayerAbilityCooldowns(playerName).put(abilityName, (System.currentTimeMillis() / 1000));
	}

	public void clearPlayerAbilityCooldowns(String playerName) {
		CacheManager.getPlayerAbilityCooldowns(playerName).clear();
	}

	public void setKitCooldown(String username, String kitName) {
		long timeKitLastUsed = System.currentTimeMillis() / 1000;
		stats.getOrCreateStatsCache(username).addKitCooldown(kitName, timeKitLastUsed);
	}

	/**
	 * When the kit was last taken. Read from the same cache that setKitCooldown
	 * writes to — reading it back from the database instead meant a cooldown set
	 * this session was invisible until the player died, so clearing the kit and
	 * picking it again skipped the wait entirely.
	 */
	private long getTimeKitLastUsed(String username, String kitName) {
		PlayerData playerData = stats.getOrCreateStatsCache(username);

		if (playerData.getKitCooldowns().containsKey(kitName)) {
			return playerData.getTimeKitLastUsed(kitName);
		}

		Object storedValue = database.getData(kitName + "_cooldowns", "last_used", username);
		long lastUsed = storedValue instanceof Number ? ((Number) storedValue).longValue() : 0L;

		// remembered either way, so this lookup happens once per session per kit
		playerData.addKitCooldown(kitName, lastUsed);
		return lastUsed;
	}

	public Cooldown getRemainingCooldown(Player p, Object type) {
		long currentTimeSeconds = (System.currentTimeMillis() / 1000);
		long timeLastUsedSeconds;
		int actionCooldownSeconds;
		Cooldown noCooldown = new Cooldown(0, 0, 0, 0);

		if (type instanceof Kit) {

			Kit kit = (Kit) type;
			if (kit.getCooldown() == null || kit.getCooldown().toSeconds() <= 0) return noCooldown;

			timeLastUsedSeconds = getTimeKitLastUsed(p.getName(), kit.getName());
			if (timeLastUsedSeconds <= 0) {
				return noCooldown;
			}
			actionCooldownSeconds = kit.getCooldown().toSeconds();

		} else if (type instanceof Ability) {

			Ability ability = (Ability) type;
			if (ability.getCooldown() == null ||
					!CacheManager.getPlayerAbilityCooldowns(p.getName()).containsKey(ability.getName()))
				return noCooldown;

			timeLastUsedSeconds = CacheManager.getPlayerAbilityCooldowns(p.getName()).get(ability.getName());
			actionCooldownSeconds = ability.getCooldown().toSeconds();

		} else {
			return noCooldown;
		}

		long cooldownRemainingSeconds = timeLastUsedSeconds + actionCooldownSeconds - currentTimeSeconds;
		if (cooldownRemainingSeconds <= 0) {
			return noCooldown;
		}
		return new Cooldown((int) cooldownRemainingSeconds);
	}

}
