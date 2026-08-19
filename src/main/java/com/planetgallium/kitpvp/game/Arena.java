package com.planetgallium.kitpvp.game;

import java.util.*;

import com.planetgallium.kitpvp.util.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;

import com.planetgallium.kitpvp.Game;

public class Arena {

	private final Game plugin;
	private final Random random;

	private final Resources resources;
	private final Resource config;

	/** One scoreboard per player, reused between updates so other plugins' boards are not thrown away. */
	private final Map<UUID, Infoboard> scoreboards;

	private final Utilities utilties;
	private final Leaderboards leaderboards;
	private final Stats stats;
	private final Kits kits;
	private final Abilities abilities;
	private final KillStreaks killstreaks;
	private final Cooldowns cooldowns;
	private final Menus menus;

	private BukkitTask scoreboardRefreshTask;

	public Arena(Game plugin, Resources resources) {
		this.plugin = plugin;
		this.random = new Random();

		this.resources = resources;
		this.config = resources.getConfig();

		this.scoreboards = new HashMap<>();

		this.utilties = new Utilities(plugin, this);
		this.leaderboards = new Leaderboards(plugin);
		this.stats = new Stats(plugin, this);
		this.kits = new Kits(plugin, this);
		this.abilities = new Abilities(plugin);
		this.killstreaks = new KillStreaks(resources);
		this.cooldowns = new Cooldowns(plugin, this);
		this.menus = new Menus(plugin, resources);
	}

	public void addPlayer(Player p, boolean toSpawn, boolean giveItems) {
		cooldowns.clearPlayerAbilityCooldowns(p.getName());

		kits.resetPlayerKit(p.getName());

		if (config.getBoolean("Arena.ResetKillStreakOnLeave")) {
			killstreaks.setStreak(p, 0);
		}

		if (config.getBoolean("Arena.ClearPotionEffectsOnJoin")) {
			for (PotionEffect effect : p.getActivePotionEffects()) {
				p.removePotionEffect(effect.getType());
			}
		}

		if (p.getFireTicks() > 0) {
			p.setFireTicks(0);
		}

		p.setGameMode(GameMode.SURVIVAL);

		if (config.getBoolean("Arena.ResetMaxHealthOnDeath")) {
			Toolkit.setMaxHealth(p, 20);
		}

		if (config.getBoolean("Arena.FancyDeath")) {
			p.setHealth(Toolkit.getMaxHealth(p));
		}

		p.setExp(0f);
		p.setFoodLevel(20);

		if (giveItems) {
			giveArenaItems(p);
		}

		if (toSpawn) {
			toSpawn(p, p.getWorld().getName());
		}

		if (resources.getScoreboard().getBoolean("Scoreboard.General.Enabled")) {
			updateScoreboards(p, false);
		}
	}

	public void removePlayer(Player p) {
		CacheManager.getPlayerAbilityCooldowns(p.getName()).clear();
		CacheManager.getPotionSwitcherUsers().remove(p.getName());

		for (PotionEffect effect : p.getActivePotionEffects()) {
			p.removePotionEffect(effect.getType());
		}

		kits.resetPlayerKit(p.getName());

		if (config.getBoolean("Arena.ResetKillStreakOnLeave")) {
			getKillStreaks().resetStreak(p);
		}

		p.setExp(0f);
		p.setFoodLevel(20);

		if (resources.getScoreboard().getBoolean("Scoreboard.General.Enabled")) {
			updateScoreboards(p, true);
		}

		stats.pushCachedStatsToDatabase(p.getName(), false); // cached stats are pushed to database on death
	}

	public void deletePlayer(Player p) {
		if (config.getBoolean("Arena.ClearInventoryOnLeave")) {
			p.getInventory().clear();
			p.getInventory().setArmorContents(null);
		}

		CacheManager.getPlayerAbilityCooldowns(p.getName()).clear();
		CacheManager.getPotionSwitcherUsers().remove(p.getName());
		scoreboards.remove(p.getUniqueId());
		killstreaks.forget(p.getName());
		plugin.getCombatTagger().clear(p);
		stats.pushCachedStatsToDatabase(p.getName(), true);
	}

	/**
	 * Everything that happens when one player kills another, in one place, so
	 * stats, experience, kill streaks and reward commands can never disagree
	 * about who got the kill.
	 */
	public void creditKill(Player killer, Player victim) {
		if (killer == null || victim == null || killer.getName().equals(victim.getName())) {
			return;
		}

		stats.addToStat("kills", killer.getName(), 1);
		stats.addExperience(killer, resources.getLevels().getInt("Levels.Options.Experience-Given-On-Kill"));

		killstreaks.addKill(killer);

		List<String> killCommands = Toolkit.replaceInList(config.getStringList("Kill.Commands"),
				"%victim%", victim.getName());
		Toolkit.runCommands(killer, killCommands, "%killer%", killer.getName());

		if (resources.getScoreboard().getBoolean("Scoreboard.General.Enabled")) {
			new BukkitRunnable() {
				@Override
				public void run() {
					if (killer.isOnline()) {
						updateScoreboards(killer, false);
					}
				}
			}.runTaskLater(plugin, 20L);
		}
	}

	public void giveArenaItems(Player p) {
		ConfigurationSection items = config.getConfigurationSection("Items");

		if (items == null) {
			return;
		}

		for (String identifier : items.getKeys(false)) {
			String itemPath = "Items." + identifier;

			if (!config.getBoolean(itemPath + ".Enabled")) {
				continue;
			}

			ItemStack item = Toolkit.safeItemStack(config.fetchString(itemPath + ".Material"));
			ItemMeta meta = item.getItemMeta();

			if (meta != null) {
				meta.setDisplayName(config.fetchString(itemPath + ".Name"));
				meta.setLore(config.getStringList(itemPath + ".Lore"));
				item.setItemMeta(meta);
			}

			int slot = config.getInt(itemPath + ".Slot");

			// If config is using Bukkit armor indices:
			if (slot == 39) { p.getInventory().setHelmet(item); continue; }
			if (slot == 38) { p.getInventory().setChestplate(item); continue; }
			if (slot == 37) { p.getInventory().setLeggings(item); continue; }
			if (slot == 36) { p.getInventory().setBoots(item); continue; }
			// Offhand
			if (slot == 40) {
				if (Toolkit.versionToNumber() >= 19) {
					p.getInventory().setItemInOffHand(item);
				}
				continue;
			}

			if (slot < 0 || slot > 35) {
				Toolkit.warnOnce("item-slot:" + itemPath,
						"&7[&b&lKIT-PVP&7] &cSlot " + slot + " for " + itemPath + " is not a valid inventory " +
						"slot; use 0-35, or 36-40 for armour and offhand.");
				continue;
			}

			p.getInventory().setItem(slot, item);
		}
	}

	public boolean toSpawn(Player p, String arenaName) {
		Location spawnLocation = getRandomSpawnLocation(arenaName);

		if (spawnLocation == null) {
			p.sendMessage(resources.getMessages().fetchString("Messages.Error.Arena")
					.replace("%arena%", arenaName));
			return false;
		}

		p.teleport(spawnLocation);
		return true;
	}

	/** A usable spawn location for the arena, or null when there is none to teleport to. */
	public Location getRandomSpawnLocation(String arenaName) {
		if (!config.contains("Arenas." + arenaName)) {
			return null;
		}

		String spawnKey = generateRandomArenaSpawn(arenaName);
		if (spawnKey == null) {
			Toolkit.warnOnce("empty-arena:" + arenaName,
					"&7[&b&lKIT-PVP&7] &cArena " + arenaName + " has no spawn points. Add one with /kp addspawn.");
			return null;
		}

		return Toolkit.getLocationFromResource(config, "Arenas." + arenaName + "." + spawnKey);
	}

	public void updateScoreboards(Player p, boolean hide) {
		if (hide) {
			scoreboards.remove(p.getUniqueId());
			if (Bukkit.getScoreboardManager() != null) {
				p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
			}
			return;
		}

		String scoreboardTitle = utilties.addPlaceholdersIfPossible(p,
				resources.getScoreboard().fetchString("Scoreboard.General.Title"));

		Infoboard scoreboard = scoreboards.get(p.getUniqueId());

		// build a fresh board only when there is not already one of ours in use
		if (scoreboard == null || p.getScoreboard() != scoreboard.getScoreboard()) {
			Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
			scoreboard = new Infoboard(board, scoreboardTitle);
			scoreboards.put(p.getUniqueId(), scoreboard);
		}

		scoreboard.setTitle(scoreboardTitle);
		scoreboard.clear();

		for (String line : resources.getScoreboard().getStringList("Scoreboard.Lines")) {
			scoreboard.add(utilties.addPlaceholdersIfPossible(p, line));
		}

		scoreboard.update(p);
	}

	/**
	 * Refreshes sidebars on an interval so time-based placeholder lines do not sit
	 * frozen between kills. Scoreboard.General.RefreshSeconds of 0 turns it off.
	 */
	public void startScoreboardRefresh() {
		stopScoreboardRefresh();

		int refreshSeconds = resources.getScoreboard().getInt("Scoreboard.General.RefreshSeconds");
		if (refreshSeconds <= 0 || !resources.getScoreboard().getBoolean("Scoreboard.General.Enabled")) {
			return;
		}

		this.scoreboardRefreshTask = new BukkitRunnable() {
			@Override
			public void run() {
				for (Player online : Bukkit.getOnlinePlayers()) {
					if (Toolkit.inArena(online) && scoreboards.containsKey(online.getUniqueId())) {
						updateScoreboards(online, false);
					}
				}
			}
		}.runTaskTimer(plugin, refreshSeconds * 20L, refreshSeconds * 20L);
	}

	public void stopScoreboardRefresh() {
		if (scoreboardRefreshTask != null) {
			scoreboardRefreshTask.cancel();
			scoreboardRefreshTask = null;
		}
	}

	/** A random spawn key for the arena, or null when the arena has no spawns configured. */
	public String generateRandomArenaSpawn(String arenaName) {
		ConfigurationSection section = config.getConfigurationSection("Arenas." + arenaName);

		if (section == null) {
			return null;
		}

		List<String> spawnKeys = new ArrayList<>(section.getKeys(false));
		if (spawnKeys.isEmpty()) {
			return null;
		}

		return spawnKeys.get(random.nextInt(spawnKeys.size()));
	}

	public Stats getStats() { return stats; }

	public Utilities getUtilities() { return utilties; }

	public Leaderboards getLeaderboards() { return leaderboards; }

	public Kits getKits() { return kits; }

	public Abilities getAbilities() { return abilities; }

	public KillStreaks getKillStreaks() { return killstreaks; }

	public Cooldowns getCooldowns() { return cooldowns; }

	public Menus getMenus() { return menus; }

}
