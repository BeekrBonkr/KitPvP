package com.planetgallium.kitpvp.util;

import java.io.File;
import java.util.*;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.api.Ability;

public class Resources {

	private static final String YAML_SUFFIX = ".yml";

	private final Game plugin;
	private final Map<String, Resource> kitToResource;
	private final Map<String, Resource> abilityToResource;

	private final Resource config, abilities, killstreaks,
							levels, menu, messages, scoreboard, signs;

	public Resources(Game plugin) {
		this.plugin = plugin;
		this.kitToResource = new LinkedHashMap<>();
		this.abilityToResource = new LinkedHashMap<>();

		Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &7Loading configuration files...");
		this.config = new Resource(plugin, "config.yml");
		this.abilities = new Resource(plugin, "abilities.yml");
		this.killstreaks = new Resource(plugin, "killstreaks.yml");
		this.levels = new Resource(plugin, "levels.yml");
		this.menu = new Resource(plugin, "menu.yml");
		this.messages = new Resource(plugin, "messages.yml");
		this.scoreboard = new Resource(plugin, "scoreboard.yml");
		this.signs = new Resource(plugin, "signs.yml");

		if (!plugin.getDataFolder().exists()) {
			for (String bundledKit : new String[]{"Fighter", "Archer", "Tank", "Soldier", "Bomber", "Kangaroo",
					"Warper", "Witch", "Ninja", "Thunderbolt", "Vampire", "Rhino", "Example", "Trickster",
					"Windcaller", "Poseidon", "Arbalist", "Warsmith", "Skybreaker"}) {
				kitToResource.put(bundledKit + YAML_SUFFIX, new Resource(plugin, "kits/" + bundledKit + YAML_SUFFIX));
			}

			for (String bundledAbility : new String[]{"Windcaller", "Poseidon", "Arbalist", "Warsmith", "Skybreaker",
					"HealthPack", "ExampleAbility", "ExampleAbility2", "SpeedBoost", "Stampede"}) {
				abilityToResource.put(bundledAbility + YAML_SUFFIX,
						new Resource(plugin, "abilities/" + bundledAbility + YAML_SUFFIX));
			}
		}

		Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &7Loading kit files...");
		load();
	}

	public void load() {
		config.load();
		abilities.load();
		killstreaks.load();
		levels.load();
		menu.load();
		messages.load();
		scoreboard.load();
		signs.load();

		messages.addCopyDefaultExemption("Messages.Stats.Message");
		messages.copyDefaults();

		menu.addCopyDefaultExemption("Menu.Items");
		menu.copyDefaults();

		levels.addCopyDefaultExemption("Levels.Levels.10.Commands");
		levels.addCopyDefaultExemption("Levels.Levels.10.Experience-To-Level-Up");
		levels.copyDefaults();

		scoreboard.addCopyDefaultExemption("Scoreboard.Lines");
		scoreboard.copyDefaults();

		config.addCopyDefaultExemption("Items.Kits.Commands");
		config.addCopyDefaultExemption("Items.Leave.Commands");
		config.copyDefaults();

		abilities.copyDefaults();
		signs.copyDefaults();

		// load new kits & abilities that have been added through file system (when doing /kp reload)
		for (String fileName : getPluginDirectoryFiles("kits", true)) {
			if (!kitToResource.containsKey(fileName)) {
				kitToResource.put(fileName, new Resource(plugin, "kits/" + fileName));
			}
		}

		for (String fileName : getPluginDirectoryFiles("abilities", true)) {
			if (!abilityToResource.containsKey(fileName)) {
				abilityToResource.put(fileName, new Resource(plugin, "abilities/" + fileName));
			}
		}

		// drop resources whose file has been deleted from disk since the last load
		kitToResource.keySet().removeIf(fileName -> !new File(plugin.getDataFolder(), "kits/" + fileName).exists());
		abilityToResource.keySet()
				.removeIf(fileName -> !new File(plugin.getDataFolder(), "abilities/" + fileName).exists());

		// Reload all kitName.yml, abilityName.yml
		kitToResource.values().forEach(Resource::load);
		abilityToResource.values().forEach(Resource::load);
	}

	public void reload() {
		load();
	}

	public void addResource(String fileName, Resource resource) {
		kitToResource.put(fileName, resource);
		kitToResource.get(fileName).load();
	}

	public void removeResource(String fileName) {
		Resource resource = kitToResource.remove(fileName);
		if (resource != null && resource.getFile().exists() && !resource.getFile().delete()) {
			Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cCould not delete " + resource.getFile().getName() + ".");
		}
	}

	public void addAbilityResource(Ability ability) {
		String fileName = ability.getName() + YAML_SUFFIX;
		Resource abilityResource = new Resource(plugin, "abilities/" + fileName);
		ability.toResource(abilityResource);

		abilityToResource.put(fileName, abilityResource);
		abilityToResource.get(fileName).load();
	}

	public Resource getKit(String kitName) {
		String resolvedName = resolveKitName(kitName);
		return resolvedName == null ? null : kitToResource.get(resolvedName + YAML_SUFFIX);
	}

	/**
	 * The kit name as it is actually spelled on disk, matched without regard to
	 * case so that "fighter" finds Fighter.yml. Null when no such kit exists.
	 */
	public String resolveKitName(String kitName) {
		if (kitName == null) {
			return null;
		}

		String wanted = kitName + YAML_SUFFIX;
		for (String fileName : kitToResource.keySet()) {
			if (fileName.equalsIgnoreCase(wanted)) {
				return trimYamlSuffix(fileName);
			}
		}
		return null;
	}

	public List<String> getKitNames() {
		List<String> kitNames = new ArrayList<>();
		for (String fileName : kitToResource.keySet()) {
			kitNames.add(trimYamlSuffix(fileName));
		}
		Collections.sort(kitNames);
		return kitNames;
	}

	/** Strips a trailing ".yml" — unlike split(".yml"), which is a regex and mangles names containing "yml". */
	public static String trimYamlSuffix(String fileName) {
		if (fileName != null && fileName.toLowerCase().endsWith(YAML_SUFFIX)) {
			return fileName.substring(0, fileName.length() - YAML_SUFFIX.length());
		}
		return fileName;
	}

	public List<String> getPluginDirectoryFiles(String directoryName, boolean withFileEndings) {
		File folder = new File(plugin.getDataFolder(), directoryName);
		List<String> fileList = new ArrayList<>();

		String[] contents = folder.exists() ? folder.list() : null;
		if (contents != null) {
			for (String fileName : contents) {
				if (fileName.startsWith(".") || !fileName.toLowerCase().endsWith(YAML_SUFFIX)) {
					continue; // hidden files, editor backups, anything that is not a config
				}
				fileList.add(withFileEndings ? fileName : trimYamlSuffix(fileName));
			}
		}
		return fileList;
	}

	public Resource getConfig() { return config; }

	public Resource getAbilities() { return abilities; }

	public Resource getKillStreaks() { return killstreaks; }

	public Resource getLevels() { return levels; }

	public Resource getMenu() { return menu; }

	public Resource getMessages() { return messages; }

	public Resource getScoreboard() { return scoreboard; }

	public Resource getSigns() { return signs; }

	public Collection<Resource> getAbilityResources() { return abilityToResource.values(); }

	public Collection<Resource> getKitResources() { return kitToResource.values(); }

}
