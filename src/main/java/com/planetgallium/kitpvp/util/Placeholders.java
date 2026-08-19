package com.planetgallium.kitpvp.util;

import com.planetgallium.database.TopEntry;
import com.planetgallium.kitpvp.Game;
import org.apache.commons.lang.StringUtils;
import org.bukkit.entity.Player;
import com.planetgallium.kitpvp.game.Arena;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class Placeholders extends PlaceholderExpansion {

	private final Game plugin;
	private final Arena arena;
	private final Map<String, String> placeholderAPItoBuiltIn;

	public Placeholders(Game plugin) {
		this.plugin = plugin;
		this.arena = plugin.getArena();
		this.placeholderAPItoBuiltIn = new HashMap<>();

		placeholderAPItoBuiltIn.put("stats_kills", "%kills%");
		placeholderAPItoBuiltIn.put("stats_deaths", "%deaths%");
		placeholderAPItoBuiltIn.put("stats_kd", "%kd%");
		placeholderAPItoBuiltIn.put("stats_experience", "%xp%");
		placeholderAPItoBuiltIn.put("stats_level", "%level%");
		placeholderAPItoBuiltIn.put("player_killstreak", "%streak%");
		placeholderAPItoBuiltIn.put("player_kit", "%kit%");
		placeholderAPItoBuiltIn.put("max_level", "%max_level%");
		placeholderAPItoBuiltIn.put("max_xp", "%max_xp%");
		placeholderAPItoBuiltIn.put("level_prefix", "%level_prefix%");
		placeholderAPItoBuiltIn.put("combat_seconds", "%combat%");
	}
	
	@Override
	public String onPlaceholderRequest(Player p, @NotNull String identifier) {
		if (identifier.startsWith("top_")) {
			return handleLeaderboardPlaceholder(identifier);
		}

		if (p != null) {
			return translatePlaceholderAPIPlaceholders(identifier, p.getName());
		}
		return null;
	}

	/** Expects top_<type>_<player|amount>_<rank>; anything shorter is reported, not thrown. */
	private String handleLeaderboardPlaceholder(String identifier) {
		String[] queries = identifier.split("_");

		if (queries.length < 4) {
			Toolkit.warnOnce("placeholder:" + identifier,
					"&7[&b&lKIT-PVP&7] &cThe placeholder %kitpvp_" + identifier + "% is incomplete. " +
					"Use %kitpvp_top_<kills|deaths|level>_<player|amount>_<rank>%.");
			return "invalid-placeholder";
		}

		String topType = queries[1]; // ex: kills
		String topIdentifier = queries[2]; // ex: amount, player
		String possibleTopValue = queries[3]; // 1, 15
		int topValue = 1; // if number parsing fails, use top player

		if (StringUtils.isNumeric(possibleTopValue)) {
			topValue = Integer.parseInt(possibleTopValue);
		} else {
			Toolkit.warnOnce("placeholder-number:" + identifier,
					"&7[&b&lKIT-PVP&7] &cFailed to properly parse placeholder, " +
					"expected number but received: " + possibleTopValue);
		}

		TopEntry entry = arena.getLeaderboards().getTopN(topType, topValue);
		boolean isUsernamePlaceholder = topIdentifier.equals("player");

		return isUsernamePlaceholder ? entry.getIdentifier() : String.valueOf(entry.getValue());
	}

	public String translatePlaceholderAPIPlaceholders(String placeholderAPIIdentifier, String username) {
		if (placeholderAPItoBuiltIn.containsKey(placeholderAPIIdentifier)) {
			String toBuiltInPlaceholder = placeholderAPItoBuiltIn.get(placeholderAPIIdentifier);
			return arena.getUtilities().replaceBuiltInPlaceholdersIfPresent(toBuiltInPlaceholder, username);
		} else {
			Toolkit.warnOnce("placeholder-unknown:" + placeholderAPIIdentifier,
					String.format("&7[&b&lKIT-PVP&7] &cUnknown placeholder identifier [%s]. " +
							"Please see plugin page.", placeholderAPIIdentifier));
			return "invalid-placeholder";
		}
	}
	
	@Override
	public boolean canRegister() { return true; }

	@Override
	public boolean persist() { return true; }

	@Override
	public @NotNull String getAuthor() { return "Cervinakuy"; }
	
	@Override
	public @NotNull String getIdentifier() { return "kitpvp"; }
	
	@Override
	public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
	
}
