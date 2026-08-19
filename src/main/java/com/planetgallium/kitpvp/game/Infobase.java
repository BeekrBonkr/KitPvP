package com.planetgallium.kitpvp.game;

import com.planetgallium.database.*;
import com.planetgallium.database.Record;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.util.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Infobase {

    private final Game plugin;
    private final Resources resources;

    private final Database database;
    private final Table statsTable;

    private final static int UUID_MAX_CHARACTERS = 36;
    private final static int USERNAME_MAX_CHARACTERS = 16;

    public Infobase(Game plugin) {
        this.plugin = plugin;
        this.resources = plugin.getResources();
        this.database = setupDatabase(resources.getConfig());

        // Stats
        this.statsTable = database.createTable("stats", new Record(
                new Field("uuid", DataType.FIXED_STRING, Infobase.UUID_MAX_CHARACTERS),
                new Field("username", DataType.STRING, Infobase.USERNAME_MAX_CHARACTERS),
                new Field("kills", DataType.INTEGER),
                new Field("deaths", DataType.INTEGER),
                new Field("experience", DataType.INTEGER),
                new Field("level", DataType.INTEGER)));

        // Kit Cooldowns
        for (String kitName : resources.getPluginDirectoryFiles("kits", false)) {
            addKitCooldownTable(kitName);
        }
    }

    private Database setupDatabase(Resource config) {
        Toolkit.printToConsole("&7[&b&lKIT-PVP&7] Establishing database connection...");

        // the local database lives beside the config files rather than wherever the
        // server happened to be started from
        String localDatabasePath = new File(plugin.getDataFolder(), "storage.db").getPath();

        if (config.fetchString("Storage.Type").equalsIgnoreCase("mysql")) {
            String host = config.fetchString("Storage.MySQL.Host");
            int port = config.getInt("Storage.MySQL.Port");
            String databaseName = config.fetchString("Storage.MySQL.Database");
            String username = config.fetchString("Storage.MySQL.Username");
            String password = config.fetchString("Storage.MySQL.Password");

            return new Database(host, port, databaseName, username, password, localDatabasePath);
        } else {
            return new Database(localDatabasePath);
        }
    }

    public boolean isPlayerRegistered(String username) {
        String uuid = usernameToUUID(username);
        return uuid != null && tableContainsUUID("stats", uuid);
    }

    /**
     * Inserts the player's row, or refreshes the stored username if they have
     * renamed since last time. Takes plain values rather than a Player so it can
     * run off the main thread.
     */
    public void registerPlayerStats(String uuid, String username, int startingLevel) {
        if (!verifyTableExists("stats")) {
            return;
        }

        Field uuidField = uuidFieldFromUUID(uuid);
        Record playerRecord = statsTable.getRecord(uuidField);

        if (playerRecord == null) {
            Record statsRecord = new Record(
                    uuidField,
                    new Field("username", DataType.STRING, username, Infobase.USERNAME_MAX_CHARACTERS),
                    new Field("kills", DataType.INTEGER, 0),
                    new Field("deaths", DataType.INTEGER, 0),
                    new Field("experience", DataType.INTEGER, 0),
                    new Field("level", DataType.INTEGER, startingLevel));
            statsTable.insertRecord(statsRecord);
        } else {
            String storedUsername = (String) playerRecord.getFieldValue("username");

            if (storedUsername == null || !storedUsername.equals(username)) {
                Field updatedUsernameField = new Field("username", DataType.STRING, username,
                        Infobase.USERNAME_MAX_CHARACTERS);
                statsTable.updateRecord(uuidField, updatedUsernameField);
            }
        }
    }

    public void exportStats() {
        Resource statsResource = new Resource(plugin, "stats.yml");
        statsResource.load();

        ConfigurationSection statsSection = statsResource.getConfigurationSection("Stats.Players");

        if (statsSection == null) {
            Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cThere is no stats.yml with a Stats.Players section " +
                    "to import from.");
            return;
        }

        for (String uuid : statsSection.getKeys(false)) {
            ConfigurationSection playerSection = statsSection.getConfigurationSection(uuid);
            if (playerSection == null) {
                continue;
            }

            Field uuidField = uuidFieldFromUUID(uuid);
            Field usernameField = new Field("username", DataType.STRING,
                    playerSection.getString("Username"), Infobase.USERNAME_MAX_CHARACTERS);
            Field killsField = new Field("kills", DataType.INTEGER, playerSection.getInt("Kills"));
            Field deathsField = new Field("deaths", DataType.INTEGER, playerSection.getInt("Deaths"));
            Field experienceField =
                    new Field("experience", DataType.INTEGER, playerSection.getInt("Experience"));
            Field levelField = new Field("level", DataType.INTEGER, playerSection.getInt("Level"));

            Record playerStatsRecord = new Record(uuidField, usernameField, killsField, deathsField, experienceField,
                    levelField);

            statsTable.updateOrInsertRecord(playerStatsRecord);
        }

        File renamedStatsFile = new File(plugin.getDataFolder(), "old_stats.yml");
        if (!statsResource.getFile().renameTo(renamedStatsFile)) {
            Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cThere was a problem renaming stats.yml to old_stats.yml.");
        }
    }

    public String usernameToUUID(String username) {
        if (username == null) {
            return null;
        }

        if (CacheManager.getUUIDCache().containsKey(username)) {
            return CacheManager.getUUIDCache().get(username);
        }

        // an online player's UUID is authoritative; also repairs a cache entry missed on join
        Player onlinePlayer = Bukkit.getPlayerExact(username);
        if (onlinePlayer != null) {
            String uuid = onlinePlayer.getUniqueId().toString();
            CacheManager.getUUIDCache().put(username, uuid);
            return uuid;
        }

        if (verifyTableExists("stats")) {
            Table stats = database.getTable("stats");

            List<Record> matchingRecords = stats.searchRecords(usernameField(username));
            if (matchingRecords.size() == 1) {
                Record matchingRecord = matchingRecords.get(0);

                String uuid = (String) matchingRecord.getFieldValue("uuid");
                CacheManager.getUUIDCache().put(username, uuid);
                return uuid;
            } else if (matchingRecords.size() > 1) {
                Toolkit.printToConsole(String.format("&7[&b&lKIT-PVP&7] &cFound %d stats rows for username %s; " +
                        "cannot resolve a UUID. Remove the duplicate rows from the stats table.",
                        matchingRecords.size(), username));
            }
        }
        return null;
    }

    private boolean tableContainsUUID(String tableName, String uuid) {
        if (verifyTableExists(tableName)) {
            Table table = database.getTable(tableName);
            return table.getRecord(uuidFieldFromUUID(uuid)) != null;
        }
        return false;
    }

    public List<TopEntry> getTopNStats(String identifier, int n) {
        if (verifyTableExists("stats")) {
            Table table = database.getTable("stats");

            return table.getTopN(new Field(identifier, DataType.INTEGER),
                    new Field("username", DataType.FIXED_STRING, Infobase.USERNAME_MAX_CHARACTERS), n);
        }
        return new ArrayList<>();
    }

    public void addKitCooldownTable(String kitName) {
        if (!isSafeTableName(kitName)) {
            Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cKit name [" + kitName + "] cannot be used as a database " +
                    "table name; cooldowns for it will not be stored. Use letters, numbers and underscores only.");
            return;
        }

        database.createTable(kitName + "_cooldowns", new Record(
                        new Field("uuid", DataType.FIXED_STRING, Infobase.UUID_MAX_CHARACTERS),
                        new Field("last_used", DataType.INTEGER)));
    }

    public void deleteKitCooldownTable(String kitName) {
        String kitCooldownTableName = kitName + "_cooldowns";
        if (verifyTableExists(kitCooldownTableName)) {
            database.deleteTable(kitCooldownTableName);
        }
        cleanupUnusedKitCooldownTables();
    }

    public void cleanupUnusedKitCooldownTables() {
        for (String tableName : new ArrayList<>(database.getTables().keySet())) {
            if (tableName.endsWith("_cooldowns")) {
                String kitName = tableName.substring(0, tableName.length() - "_cooldowns".length());
                if (!plugin.getArena().getKits().isKit(kitName)) {
                    database.deleteTable(tableName);
                }
            }
        }
    }

    /** Table names are pasted into DDL, so only plain identifiers are ever accepted. */
    private boolean isSafeTableName(String name) {
        if (name == null || name.isEmpty() || name.length() > 48) {
            return false;
        }
        for (char c : name.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    public void setData(String tableName, String identifier, Object data, DataType type, String username) {
        if (verifyTableExists(tableName)) {
            Table table = database.getTable(tableName);

            Field uuidField = uuidFieldFromUsername(username);
            if (uuidField == null) {
                return;
            }

            Record record = table.getRecord(uuidField);
            Field fieldToUpdate = new Field(identifier, type, data);

            if (record != null) {
                table.updateRecord(uuidField, fieldToUpdate);
            } else {
                System.out.printf("[Database] Failed to set data; database does not contain player %s\n", username);
            }
        }
    }

    /** Writes a player's stats and kit cooldowns. Safe to call off the main thread. */
    public void setStatsData(String uuid, PlayerData playerData) {
        if (uuid == null) {
            return;
        }

        if (verifyTableExists("stats")) {
            Table statsTable = database.getTable("stats");
            Field uuidField = uuidFieldFromUUID(uuid);

            List<Field> fieldsToUpdate = new ArrayList<>();
            for (String statIdentifier : playerData.getData().keySet()) {
                fieldsToUpdate.add(new Field(statIdentifier, DataType.INTEGER,
                        playerData.getData(statIdentifier)));
            }

            // by doing this, it updates a whole stat row with one single SQL statement
            statsTable.updateRecord(uuidField, fieldsToUpdate.toArray(new Field[0]));
        }

        // Cooldowns
        for (Map.Entry<String, Long> entry : playerData.getKitCooldowns().entrySet()) {
            String kitNameWithCooldown = entry.getKey();
            long timeKitLastUsed = entry.getValue();

            if (timeKitLastUsed <= 0) {
                continue; // a kit that has never been taken has nothing worth storing
            }

            String kitCooldownTableName = kitNameWithCooldown + "_cooldowns";
            if (verifyTableExists(kitCooldownTableName)) {
                Table kitCooldownTable = database.getTable(kitCooldownTableName);
                Record cooldownRecord = new Record(uuidFieldFromUUID(uuid),
                        new Field("last_used", DataType.INTEGER, timeKitLastUsed));

                kitCooldownTable.updateOrInsertRecord(cooldownRecord);
            }
        }
    }

    public Object getData(String tableName, String identifier, String username) {
        if (verifyTableExists(tableName)) {
            Table table = database.getTable(tableName);
            Field uuidField = uuidFieldFromUsername(username);

            if (uuidField == null) {
                return null;
            }

            Record record = table.getRecord(uuidField);
            if (record != null) {
                return record.getFieldValue(identifier);
            }
        }
        return null;
    }

    public PlayerData recordToPlayerData(Record playerRecord) {
        PlayerData playerData = new PlayerData(0, 0, 0, 0);

        if (playerRecord != null) {
            for (String statIdentifier : new ArrayList<>(playerData.getData().keySet())) {
                Object value = playerRecord.getFieldValue(statIdentifier);
                playerData.setData(statIdentifier, value instanceof Number ? ((Number) value).intValue() : 0);
            }
        }
        return playerData;
    }

    /** Reads a stats row by UUID when one is known, falling back to a username lookup. */
    public PlayerData getStatsData(String uuid, String username) {
        if (verifyTableExists("stats")) {
            Table statsTable = database.getTable("stats");

            Field uuidField = uuid != null ? uuidFieldFromUUID(uuid) : uuidFieldFromUsername(username);
            if (uuidField != null) {
                Record playerRecord = statsTable.getRecord(uuidField);
                if (playerRecord != null) {
                    return recordToPlayerData(playerRecord);
                }
            }
        }
        return new PlayerData(0, 0, 0, 0);
    }

    private boolean verifyTableExists(String tableName) {
        if (database.getTable(tableName) != null) {
            return true;
        }
        return false;
    }

    private Field uuidFieldFromUsername(String username) {
        String uuid = usernameToUUID(username);
        return uuid == null ? null : uuidFieldFromUUID(uuid);
    }

    private Field uuidFieldFromUUID(String uuid) {
        return new Field("uuid", DataType.FIXED_STRING, uuid, Infobase.UUID_MAX_CHARACTERS);
    }

    private Field usernameField(String value) {
        return new Field("username", DataType.STRING, value, Infobase.USERNAME_MAX_CHARACTERS);
    }

}
