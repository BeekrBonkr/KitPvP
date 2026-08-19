package com.planetgallium.kitpvp.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class PlayerData {

    private final Map<String, Integer> data;
    private final Map<String, Long> kitCooldowns;

    /**
     * False while the real row is still being read from the database. Anything
     * counted during that window is treated as a delta and added to the stored
     * values once they arrive, so a kill in the first tick after joining is not
     * overwritten by the load that follows it.
     */
    private boolean loaded = true;
    private final Map<String, Integer> pendingAbsoluteValues = new HashMap<>();

    public PlayerData(int kills, int deaths, int experience, int level) {
        this.data = new LinkedHashMap<>();
        this.kitCooldowns = new HashMap<>();

        data.put("kills", kills);
        data.put("deaths", deaths);
        data.put("experience", experience);
        data.put("level", level);
    }

    /** An empty record that counts changes until the stored values are available. */
    public static PlayerData loading() {
        PlayerData provisional = new PlayerData(0, 0, 0, 0);
        provisional.loaded = false;
        return provisional;
    }

    /** An absolute value. While loading it is remembered so it still wins after the merge. */
    public void setData(String identifier, int value) {
        if (!loaded) {
            pendingAbsoluteValues.put(identifier, value);
        }
        data.put(identifier, value);
    }

    /**
     * A change relative to whatever is stored. Used for everything that counts up
     * (kills, deaths, experience) so that a kill in the moment between joining and
     * the row arriving is added to the stored total rather than replacing it.
     */
    public void addData(String identifier, int delta) {
        data.put(identifier, getData(identifier) + delta);
    }

    public void addKitCooldown(String kitName, long timeKitLastUsed) { kitCooldowns.put(kitName, timeKitLastUsed); }

    public int getData(String identifier) {
        Integer value = data.get(identifier);
        return value == null ? 0 : value;
    }

    public long getTimeKitLastUsed(String kitName) {
        Long value = kitCooldowns.get(kitName);
        return value == null ? 0L : value;
    }

    /**
     * Folds the values just read from the database into this record: anything
     * counted while loading is added on top, and an absolute value set in that
     * window (an admin running /kp setstats) still wins.
     */
    public void mergeLoadedValues(PlayerData fromDatabase) {
        for (Map.Entry<String, Integer> entry : fromDatabase.getData().entrySet()) {
            String identifier = entry.getKey();

            if (pendingAbsoluteValues.containsKey(identifier)) {
                data.put(identifier, pendingAbsoluteValues.get(identifier));
            } else {
                data.put(identifier, Math.max(0, entry.getValue() + getData(identifier)));
            }
        }

        for (Map.Entry<String, Long> cooldown : fromDatabase.getKitCooldowns().entrySet()) {
            kitCooldowns.putIfAbsent(cooldown.getKey(), cooldown.getValue());
        }

        pendingAbsoluteValues.clear();
        loaded = true;
    }

    /** An independent copy, so a database write can run off the main thread against stable values. */
    public PlayerData copy() {
        PlayerData copy = new PlayerData(0, 0, 0, 0);
        copy.data.clear();
        copy.data.putAll(this.data);
        copy.kitCooldowns.putAll(this.kitCooldowns);
        copy.loaded = this.loaded;
        return copy;
    }

    public boolean isLoaded() { return loaded; }

    public Map<String, Long> getKitCooldowns() { return kitCooldowns; }

    public Map<String, Integer> getData() { return data; }

}
