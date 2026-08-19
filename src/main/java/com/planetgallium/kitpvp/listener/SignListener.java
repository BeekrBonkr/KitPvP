package com.planetgallium.kitpvp.listener;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SignListener implements Listener {

	/** Only the first three lines are used, on creation and when matching. */
	private static final int MATCHED_LINES = 3;

	private final Arena arena;
	private final Resource messages;
	private final Resource signs;
	private final List<String> regularSignTypes;
	private final List<String> customSignTypes;

	public SignListener(Game plugin) {
		this.arena = plugin.getArena();
		this.messages = plugin.getResources().getMessages();
		this.signs = plugin.getResources().getSigns();
		this.regularSignTypes = new ArrayList<>(Arrays.asList("clear", "menu", "stats", "refill"));
		this.customSignTypes = new ArrayList<>(Arrays.asList("kit", "arena"));
	}

	@EventHandler
	public void onSignChange(SignChangeEvent e) {
		if (e.getLine(0) == null || !e.getLine(0).equalsIgnoreCase("[KitPvP]")) {
			return;
		}

		Player p = e.getPlayer();
		String signType = e.getLine(1) == null ? "" : e.getLine(1).toLowerCase();

		if (!regularSignTypes.contains(signType) && !customSignTypes.contains(signType)) {
			return;
		}

		// creating a working kit or arena sign is an administrative action, not
		// something anyone who can place a sign should be able to do
		if (!p.hasPermission("kp.sign.create")) {
			p.sendMessage(messages.fetchString("Messages.General.Permission")
					.replace("%permission%", "kp.sign.create"));
			e.setLine(0, "");
			return;
		}

		if (regularSignTypes.contains(signType)) {
			renameSign(e, "Signs." + Toolkit.capitalizeFirstChar(signType), null, null);
		} else {
			String placeholderKey = "%" + signType + "%";
			String placeholderValue = e.getLine(2);
			renameSign(e, "Signs." + Toolkit.capitalizeFirstChar(signType), placeholderKey, placeholderValue);
		}

		p.sendMessage(messages.fetchString("Messages.Other.Sign"));
	}

	/** A plugin sign can only be broken by someone who could have created it. */
	@EventHandler
	public void onSignBreak(BlockBreakEvent e) {
		if (e.isCancelled() || !(e.getBlock().getState() instanceof Sign)) {
			return;
		}

		Sign sign = (Sign) e.getBlock().getState();
		SignMatch match = findMatchingSignType(sign);

		if (match != null && !e.getPlayer().hasPermission("kp.sign.create")) {
			e.setCancelled(true);
			e.getPlayer().sendMessage(messages.fetchString("Messages.General.Permission")
					.replace("%permission%", "kp.sign.create"));
		}
	}

	@EventHandler
	public void onSignUse(PlayerInteractEvent e) {
		// Logic behind this specific sign feature implementation: to avoid storing signs, most signs will
		// either match what is specified in the config 100%, or essentially 95%, where the other 5% may be one word
		// that is different (for instance a specified kit name, or arena name).
		// If there isn't a 100% match, a search for that one word delta will be started and subsequently used if found.

		if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null ||
				!(e.getClickedBlock().getState() instanceof Sign)) {
			return;
		}

		Sign sign = (Sign) e.getClickedBlock().getState();
		SignMatch match = findMatchingSignType(sign);

		if (match != null) {
			executeSign(e.getPlayer(), match.signType, match.wordDelta);
			e.setCancelled(true); // prevents sign editing on 1.20+
		}
	}

	/** The configured sign type this sign matches, or null if it is just a sign. */
	private SignMatch findMatchingSignType(Sign sign) {
		ConfigurationSection signsSection = signs.getConfigurationSection("Signs");

		if (signsSection == null) {
			return null;
		}

		for (String signType : signsSection.getKeys(false)) {
			if (signType.equals("Locations")) {
				continue;
			}

			List<String[]> mismatchedLines = new ArrayList<>();

			for (int i = 0; i < MATCHED_LINES; i++) {
				String signLine = sign.getLine(i);
				String configSignLine = signs.fetchStringOrNull("Signs." + signType + ".Line-" + (i + 1));

				if (configSignLine == null) {
					continue;
				}
				configSignLine = ChatColor.translateAlternateColorCodes('&', configSignLine);

				if (!signLine.equals(configSignLine)) {
					mismatchedLines.add(new String[]{configSignLine, signLine});
				}
			}

			if (mismatchedLines.isEmpty()) {
				return new SignMatch(signType, null);
			}

			if (mismatchedLines.size() == 1) {
				String wordDelta = getWordDelta(mismatchedLines);
				if (wordDelta != null) {
					return new SignMatch(signType, wordDelta);
				}
			}
		}

		return null;
	}

	private void renameSign(SignChangeEvent e, String path, String placeholder, String placeholderValue) {
		for (int i = 0; i < MATCHED_LINES; i++) {
			String line = signs.fetchString(path + ".Line-" + (i + 1));

			if (placeholder != null && placeholderValue != null)
				line = line.replace(placeholder, placeholderValue);
			e.setLine(i, line);
		}

		String fourthLine = signs.fetchStringOrNull(path + ".Line-4");
		if (fourthLine != null) {
			e.setLine(3, ChatColor.translateAlternateColorCodes('&', fourthLine));
		}
	}

	/**
	 * The single word that differs between the configured line and the sign — the
	 * kit or arena name. Null when the lines differ by more than one word, by none
	 * at all (colour codes only), or when the sign is shorter than the template.
	 */
	private String getWordDelta(List<String[]> mismatchedLines) {
		List<String> wordDelta = new ArrayList<>();

		String rawConfigSignLine = ChatColor.stripColor(mismatchedLines.get(0)[0]);
		String rawSignLine = ChatColor.stripColor(mismatchedLines.get(0)[1]);

		String[] rawSignLineWords = rawSignLine.split(" ");
		String[] rawConfigSignLineWords = rawConfigSignLine.split(" ");

		if (rawSignLineWords.length != rawConfigSignLineWords.length) {
			return null;
		}

		for (int i = 0; i < rawConfigSignLineWords.length; i++) {
			if (!rawConfigSignLineWords[i].equals(rawSignLineWords[i])) {
				wordDelta.add(rawSignLineWords[i]);
			}
		}

		return wordDelta.size() == 1 ? wordDelta.get(0) : null;
	}

	private void executeSign(Player p, String type, String placeholder) {
		switch (type.toLowerCase()) {
			case "refill":
				arena.getMenus().getRefillMenu().open(p);
				break;
			case "clear": case "menu": case "stats":
				p.performCommand("kp " + type);
				break;
			case "kit":
				if (placeholder != null) {
					p.performCommand("kp kit " + placeholder);
				}
				break;
			case "arena":
				if (placeholder != null) {
					p.performCommand("kp arena " + placeholder);
				}
				break;
		}
	}

	private static class SignMatch {

		private final String signType;
		private final String wordDelta;

		SignMatch(String signType, String wordDelta) {
			this.signType = signType;
			this.wordDelta = wordDelta;
		}

	}

}
