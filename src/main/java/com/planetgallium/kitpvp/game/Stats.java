package com.planetgallium.kitpvp.game;

import com.planetgallium.database.TopEntry;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.api.PlayerLevelUpEvent;
import com.planetgallium.kitpvp.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class Stats {

    private final Game plugin;
    private final Infobase database;
    private final Resources resources;
    private final Resource levels;
    private final Leaderboards leaderboards;

    public Stats(Game plugin, Arena arena) {
        this.plugin = plugin;
        this.database = plugin.getDatabase();
        this.resources = plugin.getResources();
        this.levels = plugin.getResources().getLevels();
        this.leaderboards = arena.getLeaderboards();
    }

    /**
     * Registers the player and loads their stats without blocking the join.
     * A provisional record goes into the cache immediately so nothing has to
     * wait on the database; the real values are merged in when they arrive.
     */
    public void createPlayer(Player p) {
        String username = p.getName();
        String uuid = p.getUniqueId().toString();

        CacheManager.getUUIDCache().put(username, uuid);
        CacheManager.getStatsCache().putIfAbsent(username, PlayerData.loading());

        new BukkitRunnable() {
            @Override
            public void run() {
                database.registerPlayerStats(uuid, username,
                        levels.getInt("Levels.Options.Minimum-Level"));
                PlayerData storedData = database.getStatsData(uuid, username);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        PlayerData cached = CacheManager.getStatsCache().get(username);

                        if (cached == null) {
                            CacheManager.getStatsCache().put(username, storedData);
                        } else if (!cached.isLoaded()) {
                            cached.mergeLoadedValues(storedData);
                        }

                        for (String identifier : new String[]{"kills", "deaths", "level"}) {
                            leaderboards.updateRankings(identifier,
                                    new TopEntry(username, getStat(identifier, username)));
                        }

                        if (p.isOnline()) {
                            checkForLevelUps(p); // experience banked while loading may have earned a level
                        }

                        if (p.isOnline() && resources.getScoreboard().getBoolean("Scoreboard.General.Enabled")
                                && Toolkit.inArena(p)) {
                            plugin.getArena().updateScoreboards(p, false);
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    private boolean isPlayerRegistered(String username) {
        if (CacheManager.getStatsCache().containsKey(username)) { // try to use cache first to be faster
            return true;
        }
        return database.isPlayerRegistered(username);
    }

    public double getKDRatio(String username) {
        int deaths = getStat("deaths", username);
        int kills = getStat("kills", username);

        if (deaths <= 0) {
            return Toolkit.round(kills, 2); // no deaths yet: the ratio is simply the kill count
        }
        return Toolkit.round((double) kills / deaths, 2);
    }

    public void removeExperience(String username, int amount) {
        if (!levels.getBoolean("Levels.Levels.Enabled")) {
            return;
        }

        PlayerData playerData = getOrCreateStatsCache(username);

        if (!playerData.isLoaded()) {
            playerData.addData("experience", -amount);
            return;
        }

        int currentExperience = getStat("experience", username);
        setStat("experience", username, currentExperience >= amount ? currentExperience - amount : 0);
    }

    public void addExperience(Player p, int experienceToAdd) {
        if (!levels.getBoolean("Levels.Levels.Enabled")) {
            return;
        }

        // the multiplier applies to the experience being awarded, not to the
        // total the player has banked
        int awarded = applyPossibleXPMultiplier(p, experienceToAdd);
        PlayerData playerData = getOrCreateStatsCache(p.getName());

        playerData.addData("experience", awarded);
        leaderboards.updateRankings("experience", new TopEntry(p.getName(), getStat("experience", p.getName())));

        if (!playerData.isLoaded()) {
            return; // the real total is not known yet; level-ups are checked once it is
        }

        checkForLevelUps(p);
    }

    /** Levels the player up as many times as their experience allows. */
    public void checkForLevelUps(Player p) {
        if (!levels.getBoolean("Levels.Levels.Enabled")) {
            return;
        }

        while (getStat("experience", p.getName()) >= getRegularOrRelativeNeededExperience(p.getName()) &&
                getStat("level", p.getName()) < levels.getInt("Levels.Options.Maximum-Level")) {
            levelUp(p);
            Bukkit.getPluginManager().callEvent(new PlayerLevelUpEvent(p, getStat("level", p.getName())));
        }
    }

    private int applyPossibleXPMultiplier(Player p, int experience) {
        double xpMultiplier = Toolkit.getPermissionAmountDouble(p, "kp.xpmultiplier.", 1.0);
        return (int) Math.round(experience * xpMultiplier);
    }

    public void levelUp(Player p) {
        String username = p.getName();

        if (getStat("level", username) < levels.getInt("Levels.Options.Maximum-Level")) {

            int newLevel = getStat("level", username) + 1;
            int carriedOverExperience = Math.max(0,
                    getStat("experience", username) - getRegularOrRelativeNeededExperience(username));

            setStat("level", username, newLevel);
            setStat("experience", username, carriedOverExperience);

            List<String> levelUpCommands = levels.getStringList("Levels.Commands-On-Level-Up");
            Toolkit.runCommands(p, levelUpCommands, "%level%", String.valueOf(newLevel));

            if (levels.contains("Levels.Levels." + newLevel + ".Commands")) {
                List<String> commandsList = levels.getStringList("Levels.Levels." + newLevel + ".Commands");
                Toolkit.runCommands(p, commandsList, "%level%", String.valueOf(newLevel));
            }

            p.sendMessage(resources.getMessages().fetchString("Messages.Other.Level")
                                  .replace("%level%", String.valueOf(newLevel)));
            Toolkit.playSoundToPlayer(p, "ENTITY_PLAYER_LEVELUP", 1);

        } else {
            setStat("experience", username, 0);
        }
    }

    public void addToStat(String identifier, String username, int amount) {
        if (!isPlayerRegistered(username)) {
            return;
        }

        getOrCreateStatsCache(username).addData(identifier, amount);
        leaderboards.updateRankings(identifier, new TopEntry(username, getStat(identifier, username)));
    }

    public void setStat(String identifier, String username, int data) {
        if (!isPlayerRegistered(username)) {
            return;
        }

        getOrCreateStatsCache(username).setData(identifier, data);
        leaderboards.updateRankings(identifier, new TopEntry(username, data));
    }

    /**
     * Writes the cached stats out. The values are copied on the calling thread and
     * the UUID resolved here too, so the database task never touches shared state
     * or the Bukkit API from off the main thread.
     */
    public void pushCachedStatsToDatabase(String username, boolean removeFromCacheAfter) {
        PlayerData cached = CacheManager.getStatsCache().get(username);

        if (cached == null || !cached.isLoaded()) {
            return; // nothing to push, and provisional values must never overwrite stored ones
        }

        String uuid = database.usernameToUUID(username);
        PlayerData snapshot = cached.copy();

        if (removeFromCacheAfter) {
            CacheManager.getStatsCache().remove(username);
        }

        if (uuid == null) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                database.setStatsData(uuid, snapshot);
            }
        }.runTaskAsynchronously(plugin);
    }

    /**
     * Writes stats out on the calling thread. Used at shutdown, where a scheduled
     * async task would never get the chance to run.
     */
    public void pushCachedStatsToDatabaseNow(String username) {
        PlayerData cached = CacheManager.getStatsCache().get(username);

        if (cached == null || !cached.isLoaded()) {
            return;
        }

        String uuid = database.usernameToUUID(username);
        if (uuid != null) {
            database.setStatsData(uuid, cached.copy());
        }
    }

    public int getStat(String identifier, String username) {
        if (!isPlayerRegistered(username)) {
            return 0;
        }

        return getOrCreateStatsCache(username).getData(identifier);
    }

    public PlayerData getOrCreateStatsCache(String username) {
        PlayerData cached = CacheManager.getStatsCache().get(username);
        if (cached != null) {
            return cached;
        }

        if (!isPlayerRegistered(username)) {
            return new PlayerData(0, 0, 0, 0);
        }

        // an offline player looked up by an admin command: read through once and keep it
        PlayerData loadedData = database.getStatsData(null, username);
        CacheManager.getStatsCache().put(username, loadedData);
        return loadedData;
    }

    public int getRegularOrRelativeNeededExperience(String username) {
        int level = getStat("level", username);

        if (levels.contains("Levels.Levels." + level + ".Experience-To-Level-Up")) {
            return levels.getInt("Levels.Levels." + level + ".Experience-To-Level-Up");
        }
        return levels.getInt("Levels.Options.Experience-To-Level-Up");
    }

}
