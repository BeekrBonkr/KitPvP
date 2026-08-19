package com.planetgallium.kitpvp.util;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.planetgallium.kitpvp.Game;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public class Resource extends YamlConfiguration {

	private final String name;
	private final File file;
	private final List<String> copyDefaultExemptions;

	private final Plugin plugin;
	private final String path;

	public Resource(Plugin plugin, String path) {
		this.plugin = plugin;
		this.path = path;

		this.file = new File(plugin.getDataFolder() + "/" + Paths.get(path));
		this.name = Paths.get(path).getFileName().toString();
		this.copyDefaultExemptions = new ArrayList<>();
	}

	public void load() {
		if (!file.getParentFile().exists()) {
			file.getParentFile().mkdirs();
		}

		if (!file.exists()) {
			if (plugin.getResource(path) == null) {
				try {
					file.createNewFile();
				} catch (IOException e) {
					Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cCould not create " + path + ": " + e.getMessage());
				}
			} else {
				plugin.saveResource(path, true);
			}
		}

		try {
			super.load(file);
		} catch (InvalidConfigurationException e) {
			Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &c" + path + " contains invalid YAML and was not loaded. " +
					"Paste it into a YAML checker to find the problem; the plugin will use defaults until it is fixed.");
			Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &c" + e.getMessage());
		} catch (IOException e) {
			Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cCould not read " + path + ": " + e.getMessage());
		}
	}

	public void copyDefaults() {
		Reader defaultConfigSearchResult = null;

		if (plugin.getResource(path) != null) {
			try {
				defaultConfigSearchResult = new InputStreamReader(plugin.getResource(path), "UTF8");
			} catch (UnsupportedEncodingException e) {
				Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cCould not read the bundled copy of " + path + ".");
			}
		}

		if (defaultConfigSearchResult != null) {
			YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(defaultConfigSearchResult);

			for (String valuePath : defaultConfig.getValues(true).keySet()) {
				if (!contains(valuePath)) {
					if (!Toolkit.containsAnyThatStartWith(copyDefaultExemptions, valuePath)) {
						this.set(valuePath, defaultConfig.get(valuePath));
					}
				}
			}
			save();
		}
	}

	public void addCopyDefaultExemption(String path) {
		copyDefaultExemptions.add(path);
	}

	public void save() {
		try {
			super.save(file);
		} catch (IOException e) {
			Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cCould not save " + path + ": " + e.getMessage());
		}
	}

	/**
	 * A colourised string from the config. A missing path logs once and yields an
	 * empty string, so a mistyped or not-yet-added key never reaches a player as
	 * placeholder text.
	 */
	public String fetchString(String path) {
		String string = super.getString(path);

		if (string == null) {
			Toolkit.warnOnce(name + ":" + path,
					String.format("&7[&b&lKIT-PVP&7] &cNo value for %s in %s; using an empty string. " +
							"Add the path back, or run /kp reload to have the default copied in.", path, name));
			return "";
		}

		return ChatColor.translateAlternateColorCodes('&',
				string.replace("%prefix%", Game.getPrefix() == null ? "" : Game.getPrefix()));
	}

	/** The raw stored string, or null when unset. Neither colourised nor logged. */
	public String fetchStringOrNull(String path) {
		return super.getString(path);
	}

	/** A colourised string, falling back to the given default when the path is unset. */
	public String fetchString(String path, String fallback) {
		String string = super.getString(path);
		if (string == null) {
			string = fallback;
		}
		if (string == null) {
			return "";
		}
		return ChatColor.translateAlternateColorCodes('&',
				string.replace("%prefix%", Game.getPrefix() == null ? "" : Game.getPrefix()));
	}

	@Override
	public List<String> getStringList(String path) {
		return Toolkit.colorizeList(super.getStringList(path));
	}

	/** The stored list without colour translation, for values that are written back to disk. */
	public List<String> getRawStringList(String path) {
		return super.getStringList(path);
	}

	public String getName() { return name; }

	public File getFile() { return file; }

}
