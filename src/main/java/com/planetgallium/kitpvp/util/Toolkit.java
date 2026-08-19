package com.planetgallium.kitpvp.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XPotion;
import com.cryptomorin.xseries.XSound;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.api.Kit;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.potion.PotionEffectType;

public class Toolkit {

	private static final Material FALLBACK_MATERIAL = Material.BEDROCK;

	/** Names already reported as unresolvable, so a misconfiguration logs once instead of once per interaction. */
	private static final Set<String> alreadyWarnedNames = Collections.synchronizedSet(new HashSet<>());

	private static int cachedServerVersion = -1;
	private static boolean warnedAboutMissingArenas = false;

	public static boolean isEmpty(ItemStack stack) {
		return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
	}

	public static int countEmptyMainSlots(Player p) {
		int emptySlots = 0;
		for (int i = 0; i < 36; i++) {
			if (isEmpty(p.getInventory().getItem(i))) {
				emptySlots++;
			}
		}
		return emptySlots;
	}

	/**
	 * Number of tridents granted by the player's kit that are currently outside
	 * their inventory — mid-flight after a throw, stuck in a block, or lying on
	 * the ground. Each one needs an empty slot to return into (Loyalty) or be
	 * picked back up, so item inserts must leave this many slots empty. Which
	 * slot stays empty does not matter: a Loyalty trident returns to the slot
	 * it was thrown from, falling back to any free slot.
	 */
	public static int countMissingKitTridents(Kit kit, Player p) {
		if (kit == null) {
			return 0;
		}

		int kitTridents = 0;
		for (ItemStack kitItem : kit.getInventory().values()) {
			if (!isEmpty(kitItem) && kitItem.getType().name().equals("TRIDENT")) {
				kitTridents++;
			}
		}
		if (kitTridents == 0) {
			return 0;
		}

		int heldTridents = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack item = p.getInventory().getItem(i);
			if (!isEmpty(item) && item.getType().name().equals("TRIDENT")) {
				heldTridents++;
			}
		}
		if (versionToNumber() >= 19) {
			ItemStack offhand = p.getInventory().getItemInOffHand();
			if (!isEmpty(offhand) && offhand.getType().name().equals("TRIDENT")) {
				heldTridents++;
			}
		}

		return Math.max(0, kitTridents - heldTridents);
	}

	public static boolean inArena(World world) {
		if (world == null) {
			return false;
		}

		Resource config = Game.getInstance().getResources().getConfig();

		if (!config.contains("Arenas")) {
			if (!warnedAboutMissingArenas) {
				warnedAboutMissingArenas = true;
				printToConsole("&7[&b&lKIT-PVP&7] &cNo arena spawn has been set yet. " +
						"Stand where players should spawn and run &f/kp addspawn&c.");
			}
			return false;
		}

		return config.contains("Arenas." + world.getName());
	}

	public static boolean inArena(Entity entity) {
		return entity != null && inArena(entity.getWorld());
	}

	/**
	 * Closest other player in the same world that is a valid tracking target,
	 * or null if there is nobody to track. Spectators, NPCs and vanished
	 * players are skipped.
	 */
	public static Nearest getNearestPlayer(Player player, int maxY) {
		Player nearestPlayer = null;
		double nearestDistanceSquared = Double.MAX_VALUE;

		for (Player other : player.getWorld().getPlayers()) {
			if (other == player || other.getGameMode() == GameMode.SPECTATOR) {
				continue;
			}
			if (other.hasMetadata("NPC") || other.hasMetadata("vanished")) {
				continue;
			}
			if (other.getLocation().getBlockY() >= maxY) {
				continue;
			}

			double distanceSquared = player.getLocation().distanceSquared(other.getLocation());
			if (distanceSquared < nearestDistanceSquared) {
				nearestDistanceSquared = distanceSquared;
				nearestPlayer = other;
			}
		}

		return nearestPlayer == null ? null : new Nearest(nearestPlayer, Math.sqrt(nearestDistanceSquared));
	}

	public static double round(double value, int precision) {
		int scale = (int) Math.pow(10, precision);
		return (double) Math.round(value * scale) / scale;
	}

	public static Color getColorFromConfig(FileConfiguration config, String path) {
		return Color.fromRGB(config.getInt(path + ".Red"),
				config.getInt(path + ".Green"),
				config.getInt(path + ".Blue"));
	}

	public static void runCommands(Player p, List<String> commands, String replaceFrom, String replaceTo) {
		if (commands == null) return;

		for (String commandString : commands) {
			String[] commandPhrase = commandString.split(":", 2);

			if (commandPhrase.length == 1) {
				warnOnce("command-format:" + commandString,
						"&7[&b&lKIT-PVP&7] &cIncorrect command format for [" + commandString + "]. " +
						"Please see: &fhttps://bit.ly/kp-command-format");
				continue;
			}

			String sender = commandPhrase[0].trim();
			String command = commandPhrase[1].trim();

			if (sender.equalsIgnoreCase("console")) {
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
						replaceCommandPlaceholders(command, p, replaceFrom, replaceTo));
			} else if (sender.equalsIgnoreCase("player")) {
				p.performCommand(replaceCommandPlaceholders(command, p, replaceFrom, replaceTo));
			} else {
				warnOnce("command-format:" + commandString,
						"&7[&b&lKIT-PVP&7] &cUnknown command sender [" + sender + "]. " +
						"Please see: &fhttps://bit.ly/kp-command-format");
			}
		}
	}

	private static String replaceCommandPlaceholders(String s, Player p, String replaceFrom, String replaceTo) {
		String withPotentialPapiPlaceholders = addPlaceholdersIfPossible(p, s);
		return withPotentialPapiPlaceholders
				.replace("%player%", p.getName())
				.replace("%uuid%", p.getUniqueId().toString())
				.replace(replaceFrom, replaceTo);
	}

	/**
	 * The running server version in the scheme 18, 19, 110 ... 121, 122.
	 * Read from Bukkit.getBukkitVersion(), which is a stable "1.20.4-R0.1-SNAPSHOT"
	 * rather than the free-form build string returned by Bukkit.getVersion().
	 */
	public static int versionToNumber() {
		if (cachedServerVersion == -1) {
			cachedServerVersion = detectServerVersion();
		}
		return cachedServerVersion;
	}

	private static int detectServerVersion() {
		int fromBukkitVersion = parseVersionString(Bukkit.getBukkitVersion());
		if (fromBukkitVersion != -1) {
			return fromBukkitVersion;
		}

		// last resort: the free-form build string, which at least contains "(MC: 1.20.4)"
		String rawVersion = Bukkit.getVersion();
		int markerIndex = rawVersion.indexOf("MC: ");
		if (markerIndex != -1) {
			int fromBuildString = parseVersionString(rawVersion.substring(markerIndex + 4));
			if (fromBuildString != -1) {
				return fromBuildString;
			}
		}

		printToConsole("&7[&b&lKIT-PVP&7] &cCould not determine the server version from [" + rawVersion +
				"]; assuming the newest supported version.");
		return 500;
	}

	/** "1.20.4-R0.1-SNAPSHOT", "1.9" and "1.21)" all parse; returns -1 when nothing usable is found. */
	private static int parseVersionString(String version) {
		if (version == null) {
			return -1;
		}

		int major = -1;
		int minor = -1;
		int cursor = 0;

		// major
		StringBuilder digits = new StringBuilder();
		while (cursor < version.length() && Character.isDigit(version.charAt(cursor))) {
			digits.append(version.charAt(cursor++));
		}
		if (digits.length() == 0 || cursor >= version.length() || version.charAt(cursor) != '.') {
			return -1;
		}
		major = Integer.parseInt(digits.toString());
		cursor++;

		// minor
		digits.setLength(0);
		while (cursor < version.length() && Character.isDigit(version.charAt(cursor))) {
			digits.append(version.charAt(cursor++));
		}
		if (digits.length() == 0) {
			return -1;
		}
		minor = Integer.parseInt(digits.toString());

		if (major != 1 || minor < 8) {
			return -1;
		}
		return minor < 10 ? 10 + minor : 100 + minor;
	}

	/**
	 * Converts a version string like "1.9" or "1.21" to the same number
	 * scheme used by versionToNumber(). Returns -1 if the string cannot be
	 * parsed, which callers should treat as "no requirement".
	 */
	public static int versionStringToNumber(String version) {
		int parsed = parseVersionString(version == null ? null : version.trim());
		if (parsed == -1) {
			printToConsole("&7[&b&lKIT-PVP&7] &cCould not parse version string: " + version);
		}
		return parsed;
	}

	public static List<String> colorizeList(List<String> list) {
		if (list == null) {
			return null;
		}

		List<String> newList = new ArrayList<>();
		for (String string : list) {
			newList.add(string == null ? "" : ChatColor.translateAlternateColorCodes('&', string));
		}
		return newList;
	}

	public static List<String> replaceInList(List<String> list, String find, String replace) {
		List<String> newList = new ArrayList<>();

		if (list != null) {
			for (String string : list) {
				newList.add(string.replace(find, replace));
			}
		}
		return newList;
	}

	public static String toNormalColorCodes(String string) {
		if (string != null) {
			return string.replace("§", "&");
		}
		return null;
	}

	public static List<String> toNormalColorCodes(List<String> list) {
		if (list == null) {
			return null;
		}

		List<String> newList = new ArrayList<>();
		for (String line : list) {
			newList.add(toNormalColorCodes(line));
		}
		return newList;
	}

	public static Player getPlayer(World world, String name) {
		if (world == null || name == null) {
			return null;
		}

		for (Player player : world.getPlayers()) {
			if (player.getName().equals(name)) {
				return player;
			}
		}
		return null;
	}

	public static void saveLocationToResource(Resource resource, String path, Location location) {
		resource.set(path + ".World", location.getWorld().getName());
		resource.set(path + ".X", location.getBlockX());
		resource.set(path + ".Y", location.getBlockY());
		resource.set(path + ".Z", location.getBlockZ());
		resource.set(path + ".Yaw", location.getYaw());
		resource.set(path + ".Pitch", location.getPitch());
		resource.save();
	}

	/** Null when the stored world is not loaded, which callers must handle rather than teleport into. */
	public static Location getLocationFromResource(Resource resource, String path) {
		String worldName = resource.fetchStringOrNull(path + ".World");
		World world = worldName == null ? null : Bukkit.getWorld(worldName);

		if (world == null) {
			printToConsole("&7[&b&lKIT-PVP&7] &cThe world [" + worldName + "] stored at " + path +
					" is not loaded; that spawn cannot be used.");
			return null;
		}

		return new Location(world,
				resource.getInt(path + ".X") + 0.5,
				resource.getInt(path + ".Y"),
				resource.getInt(path + ".Z") + 0.5,
				(float) resource.getDouble(path + ".Yaw"),
				(float) resource.getDouble(path + ".Pitch"));
	}

	public static String addPlaceholdersIfPossible(Player player, String text) {
		if (text == null) {
			return "";
		}

		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			text = PlaceholderAPI.setPlaceholders(player, text);
		}
		return Toolkit.translate(text);
	}

	/**
	 * Highest numeric suffix among the player's permissions starting with the
	 * given prefix, or defaultValue when none is set. Permissions that are set
	 * to false are ignored, as are non-numeric suffixes such as a wildcard.
	 */
	public static double getPermissionAmountDouble(Player player, String permissionPrefix, double defaultValue) {
		double highestPermissionValue = Double.NaN;

		for (PermissionAttachmentInfo attachmentInfo : player.getEffectivePermissions()) {
			if (!attachmentInfo.getValue()) {
				continue;
			}

			String permission = attachmentInfo.getPermission();
			if (!permission.startsWith(permissionPrefix)) {
				continue;
			}

			try {
				double permissionValue = Double.parseDouble(permission.substring(permissionPrefix.length()));
				if (Double.isNaN(highestPermissionValue) || permissionValue > highestPermissionValue) {
					highestPermissionValue = permissionValue;
				}
			} catch (NumberFormatException ignored) {
				// a wildcard or another child node under this prefix; not an amount
			}
		}

		return Double.isNaN(highestPermissionValue) ? defaultValue : highestPermissionValue;
	}

	public static int getPermissionAmount(Player player, String permissionPrefix, int defaultValue) {
		if (player.isOp()) {
			return Integer.MAX_VALUE;
		}

		double highest = getPermissionAmountDouble(player, permissionPrefix, Double.NaN);
		if (Double.isNaN(highest)) {
			return defaultValue;
		}
		return (int) highest;
	}

	public static String translate(String s) {
		if (s == null) {
			return "";
		}

		String prefix = Game.getPrefix();
		return ChatColor.translateAlternateColorCodes('&',
				s.replace("%prefix%", prefix == null ? "" : prefix));
	}

	public static int getNextAvailable(FileConfiguration yamlConfig, String path, int limit, boolean zeroBased,
									   int fallbackAmount) {
		for (int i = zeroBased ? 0 : 1; i < limit; i++) {
			if (!yamlConfig.contains(path + "." + i)) {
				return i;
			}
		}
		return fallbackAmount;
	}

	public static boolean containsAnyThatStartWith(List<String> list, String valueToTest) {
		for (String string : list) {
			if (valueToTest.startsWith(string)) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasMatchingDisplayName(ItemStack item, String targetDisplayName) {
		if (isEmpty(item) || targetDisplayName == null) {
			return false;
		}

		ItemMeta meta = item.getItemMeta();
		if (meta != null && meta.hasDisplayName()) {
			return Toolkit.translate(meta.getDisplayName()).equals(Toolkit.translate(targetDisplayName));
		}
		return false;
	}

	public static boolean hasMatchingMaterial(ItemStack item, String targetMaterial) {
		if (item == null || targetMaterial == null) {
			return false;
		}
		return item.getType() == Toolkit.safeMaterial(targetMaterial);
	}

	public static boolean matchesConfigItem(ItemStack item, Resource resource, String path) {
		if (isEmpty(item)) {
			return false;
		}

		return hasMatchingMaterial(item, resource.fetchString(path + ".Material")) &&
				hasMatchingDisplayName(item, resource.fetchString(path + ".Name"));
	}

	public static void printToConsole(String message) {
		Bukkit.getConsoleSender().sendMessage(Toolkit.translate(message));
	}

	/** Prints a console warning the first time a given key produces it, then stays quiet about it. */
	public static void warnOnce(String key, String message) {
		if (alreadyWarnedNames.add(key)) {
			printToConsole(message);
		}
	}

	public static void clearWarningHistory() {
		alreadyWarnedNames.clear();
		warnedAboutMissingArenas = false;
	}

	public static String capitalizeFirstChar(String word) {
		if (word == null || word.isEmpty()) {
			return word;
		}
		return word.substring(0, 1).toUpperCase() + word.substring(1);
	}

	/**
	 * The max-health attribute, resolved by name so it keeps working across the
	 * 1.21.3 rename from GENERIC_MAX_HEALTH to MAX_HEALTH. Null on versions
	 * where the attribute API is not available at all.
	 */
	private static final Attribute MAX_HEALTH_ATTRIBUTE = resolveMaxHealthAttribute();

	private static Attribute resolveMaxHealthAttribute() {
		for (String fieldName : new String[]{"MAX_HEALTH", "GENERIC_MAX_HEALTH"}) {
			try {
				Object value = Attribute.class.getField(fieldName).get(null);
				if (value instanceof Attribute) {
					return (Attribute) value;
				}
			} catch (Throwable ignored) {
				// not present on this version; try the other name
			}
		}
		return null;
	}

	@SuppressWarnings("deprecation")
	public static void setMaxHealth(Player p, double amount) {
		if (MAX_HEALTH_ATTRIBUTE != null) {
			AttributeInstance healthAttribute = p.getAttribute(MAX_HEALTH_ATTRIBUTE);
			if (healthAttribute != null) {
				healthAttribute.setBaseValue(amount);
				if (p.getHealth() > amount) {
					p.setHealth(amount);
				}
				return;
			}
		}
		p.setMaxHealth(amount);
	}

	@SuppressWarnings("deprecation")
	public static double getMaxHealth(Player p) {
		if (MAX_HEALTH_ATTRIBUTE != null) {
			AttributeInstance healthAttribute = p.getAttribute(MAX_HEALTH_ATTRIBUTE);
			if (healthAttribute != null) {
				return healthAttribute.getValue();
			}
		}
		return p.getMaxHealth();
	}

	/**
	 * Returns true if the material name resolves to a real material on the
	 * running server version. Unlike safeMaterial, does not fall back.
	 */
	public static boolean isMaterialSupported(String materialName) {
		if (materialName == null) return true;
		try {
			Optional<XMaterial> materialOptional = XMaterial.matchXMaterial(materialName);
			if (materialOptional.isPresent() && materialOptional.get().parseMaterial() != null) {
				return true;
			}
		} catch (IllegalArgumentException ignored) {}
		return Material.getMaterial(materialName) != null;
	}

	public static Material safeMaterial(String materialName) {
		if (materialName == null) {
			return FALLBACK_MATERIAL;
		}

		// 1. Use XMaterial first
		try {
			Optional<XMaterial> materialOptional = XMaterial.matchXMaterial(materialName);
			if (materialOptional.isPresent()) {
				Material material = materialOptional.get().parseMaterial();
				if (material != null) {
					return material;
				}
			}
		} catch (IllegalArgumentException ignored) {}

		// 2. XMaterial fails, try native check before returning fallbackMaterial (forward-compatible)
		Material potentialNativeMaterial = Material.getMaterial(materialName);
		if (potentialNativeMaterial != null) {
			return potentialNativeMaterial;
		}

		// 3. Native check fails; return fallbackMaterial
		warnOnce("material:" + materialName,
				String.format("&7[&b&lKIT-PVP&7] &cUnknown material [%s], defaulting to [%s].",
						materialName, FALLBACK_MATERIAL));
		return FALLBACK_MATERIAL;
	}

	public static ItemStack safeItemStack(String materialName) {
		return new ItemStack(Toolkit.safeMaterial(materialName));
	}

	public static Sound safeSound(String soundName) {
		if (soundName != null) {
			Optional<XSound> soundOptional = XSound.matchXSound(soundName);
			if (soundOptional.isPresent()) {
				Sound sound = soundOptional.get().parseSound();
				if (sound != null) {
					return sound;
				}
			}
		}
		warnOnce("sound:" + soundName, "&7[&b&lKIT-PVP&7] &cInvalid sound: " + soundName);
		return XSound.ENTITY_ENDER_DRAGON_HURT.parseSound();
	}

	public static PotionEffectType safePotionEffectType(String potionEffectTypeName) {
		if (potionEffectTypeName != null) {
			Optional<XPotion> potionOptional = XPotion.matchXPotion(potionEffectTypeName);
			if (potionOptional.isPresent()) {
				PotionEffectType potionEffectType = potionOptional.get().getPotionEffectType();
				if (potionEffectType != null) {
					return potionEffectType;
				}
			}
		}
		warnOnce("effect:" + potionEffectTypeName,
				"&7[&b&lKIT-PVP&7] &cInvalid potion effect type: " + potionEffectTypeName);
		return XPotion.BLINDNESS.getPotionEffectType();
	}

	@SuppressWarnings("deprecation")
	public static ItemStack getHandItemForInteraction(Event e) {
		Player p;
		ItemStack handItem = null;

		if (e instanceof PlayerInteractEvent) {
			PlayerInteractEvent interactEvent = (PlayerInteractEvent) e;
			p = interactEvent.getPlayer();
			if (versionToNumber() == 18) {
				handItem = p.getItemInHand();
			} else {
				handItem = p.getInventory().getItem(interactEvent.getHand() != null ?
						interactEvent.getHand() : EquipmentSlot.HAND);
			}
		} else if (e instanceof PlayerInteractEntityEvent) {
			PlayerInteractEntityEvent interactEntityEvent = (PlayerInteractEntityEvent) e;
			p = interactEntityEvent.getPlayer();
			if (versionToNumber() == 18) {
				handItem = p.getItemInHand();
			} else {
				handItem = p.getInventory().getItem(interactEntityEvent.getHand());
			}
		}

		return handItem == null ? Toolkit.safeItemStack("AIR") : handItem;
	}

	@SuppressWarnings("deprecation")
	public static void setHandItemForInteraction(Event e, ItemStack item) {
		Player p;

		if (e instanceof PlayerInteractEvent) {
			PlayerInteractEvent interactEvent = (PlayerInteractEvent) e;
			p = interactEvent.getPlayer();
			if (versionToNumber() == 18) {
				p.setItemInHand(item);
				return;
			}

			p.getInventory().setItem(interactEvent.getHand() != null ?
					interactEvent.getHand() : EquipmentSlot.HAND, item);
		} else if (e instanceof PlayerInteractEntityEvent) {
			PlayerInteractEntityEvent interactEntityEvent = (PlayerInteractEntityEvent) e;
			p = interactEntityEvent.getPlayer();
			if (versionToNumber() == 18) {
				p.setItemInHand(item);
				return;
			}

			p.getInventory().setItem(interactEntityEvent.getHand(), item);
			// ^ no null check needed, there is always a hand with this event
		}
	}

	/** Consumes one of the interaction item, clearing the slot when the last one is used. */
	public static void consumeOneHandItem(Event e, ItemStack handItem) {
		if (isEmpty(handItem)) {
			return;
		}

		if (handItem.getAmount() <= 1) {
			setHandItemForInteraction(e, safeItemStack("AIR"));
		} else {
			handItem.setAmount(handItem.getAmount() - 1);
			setHandItemForInteraction(e, handItem);
		}
	}

	@SuppressWarnings("deprecation")
	public static boolean eitherHandHasMaterial(Player p, String materialString) {
		Material materialToFind = safeMaterial(materialString);
		if (versionToNumber() == 18) {
			return p.getItemInHand() != null && p.getItemInHand().getType() == materialToFind;
		}
		return p.getInventory().getItemInMainHand().getType() == materialToFind ||
				p.getInventory().getItemInOffHand().getType() == materialToFind;
	}

	public static void playSoundToPlayer(Player p, String soundName, int pitch) {
		// Bukkit expects a pitch between 0.5 and 2.0; configs have historically used 1 and -1
		float clampedPitch = Math.max(0.5f, Math.min(2.0f, pitch <= 0 ? 1.0f : (float) pitch));
		p.playSound(p.getLocation(), Toolkit.safeSound(soundName), 1, clampedPitch);
	}

	public static SlotWrapper getSlotUsedForInteraction(PlayerInteractEvent e) {
		if (versionToNumber() >= 19) {
			if (e.getHand() == EquipmentSlot.OFF_HAND) {
				return new SlotWrapper(e.getHand());
			}
		}
		return new SlotWrapper(e.getPlayer().getInventory().getHeldItemSlot());
	}

	public static void appendToLore(ItemStack item, String lineToAppend) {
		ItemMeta itemMeta = item.getItemMeta();
		if (itemMeta != null) {
			List<String> newLore = itemMeta.hasLore() && itemMeta.getLore() != null ?
					itemMeta.getLore() : new ArrayList<>();
			newLore.add(lineToAppend);

			itemMeta.setLore(newLore);

			item.setItemMeta(itemMeta);
		}
	}

	public static boolean singleLineLoreMatches(ItemStack item, String expectedLore) {
		if (isEmpty(item)) {
			return false;
		}

		ItemMeta itemMeta = item.getItemMeta();
		if (itemMeta != null && itemMeta.hasLore()) {
			List<String> lore = itemMeta.getLore();
			return lore != null && !lore.isEmpty() && lore.get(0).equals(expectedLore);
		}
		return false;
	}

	public static boolean isPluginEnabled(String pluginName) {
		return Bukkit.getPluginManager().isPluginEnabled(pluginName);
	}

	/** Result of a nearest-player search: the player, and how far away they were when it ran. */
	public static class Nearest {

		private final Player player;
		private final double distance;

		public Nearest(Player player, double distance) {
			this.player = player;
			this.distance = distance;
		}

		public Player getPlayer() { return player; }

		public double getDistance() { return distance; }

	}

	public static class SlotWrapper {

		private boolean usesEquipmentSlot = false;
		private int heldItemSlot = 0;
		private EquipmentSlot equipmentSlot;

		public SlotWrapper(int heldItemSlot) {
			this.heldItemSlot = heldItemSlot;
		}

		public SlotWrapper(EquipmentSlot equipmentSlot) {
			this.usesEquipmentSlot = true;
			this.equipmentSlot = equipmentSlot;
		}

		public void placeItemInSlot(Player p, ItemStack item) {
			if (usesEquipmentSlot) {
				p.getInventory().setItem(equipmentSlot, item);
			} else {
				p.getInventory().setItem(heldItemSlot, item);
			}
		}

	}

	/**
	 * Sets a potion's base type across versions. setBasePotionData is the API that
	 * carries the extended and upgraded flags and exists through 1.20; 1.21 removed
	 * it in favour of setBasePotionType. Whichever is missing throws, so both are
	 * tried in turn rather than being picked from a version number.
	 */
	public static void applyBasePotionType(org.bukkit.inventory.meta.PotionMeta meta, org.bukkit.potion.PotionType type,
										   boolean extended, boolean upgraded) {
		try {
			meta.setBasePotionData(new org.bukkit.potion.PotionData(type, extended, upgraded));
			return;
		} catch (Throwable ignored) {
			// newer server: the flags live in the potion type itself
		}

		try {
			meta.setBasePotionType(resolveVariant(type, extended, upgraded));
		} catch (Throwable ignored) {
			warnOnce("potion-api",
					"&7[&b&lKIT-PVP&7] &cThis server version accepts neither potion API; " +
					"potions will be plain water bottles.");
		}
	}

	public static void applyBasePotionType(org.bukkit.inventory.meta.PotionMeta meta,
										   org.bukkit.potion.PotionType type) {
		applyBasePotionType(meta, type, false, false);
	}

	/** LONG_ and STRONG_ variants replaced the extended and upgraded flags in 1.20.5. */
	private static org.bukkit.potion.PotionType resolveVariant(org.bukkit.potion.PotionType type,
															   boolean extended, boolean upgraded) {
		String prefix = upgraded ? "STRONG_" : extended ? "LONG_" : null;

		if (prefix != null) {
			try {
				return org.bukkit.potion.PotionType.valueOf(prefix + type.name());
			} catch (IllegalArgumentException ignored) {
				// no such variant on this version; the plain type is the best available
			}
		}
		return type;
	}

	public static int parsePotionEffectDuration(int duration) {
		if (duration == -1) { // if you put -1 in any version it makes it infinite
			boolean useNegativeOneVersion = Toolkit.versionToNumber() >= 120 ||
					Bukkit.getBukkitVersion().startsWith("1.19.4");
			return useNegativeOneVersion ? -1 : Integer.MAX_VALUE;
		}

		return duration * 20;
	}

}
