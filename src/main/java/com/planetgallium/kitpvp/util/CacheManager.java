package com.planetgallium.kitpvp.util;

import com.planetgallium.kitpvp.api.Kit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CacheManager {

    // Concurrent because the stats cache and UUID cache are read from the database
    // threads that push stats, while the main thread keeps updating them.
    private static final Map<String, String> usernameToUUID = new ConcurrentHashMap<>();
    private static final Map<String, Kit> kitCache = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, Long>> abilityCooldowns = new ConcurrentHashMap<>();
    private static final Map<String, PlayerData> statsCache = new ConcurrentHashMap<>();
    private static final Set<String> potionSwitcherUsers = ConcurrentHashMap.newKeySet();

    public static Map<String, String> getUUIDCache() { return usernameToUUID; }

    public static Map<String, Kit> getKitCache() { return kitCache; }

    public static Map<String, PlayerData> getStatsCache() { return statsCache; }

    public static Set<String> getPotionSwitcherUsers() { return potionSwitcherUsers; }

    public static Map<String, Long> getPlayerAbilityCooldowns(String username) {
        return abilityCooldowns.computeIfAbsent(username, key -> new ConcurrentHashMap<>());
    }

    /** Drops cached entries for a player who is no longer online. */
    public static void forgetPlayer(String username) {
        usernameToUUID.remove(username);
        abilityCooldowns.remove(username);
        potionSwitcherUsers.remove(username);
    }

    public static void clearCaches() {
        kitCache.clear();
        abilityCooldowns.clear();
        // stats and usernameToUUID survive a reload: they are live player state, not config
    }

}
