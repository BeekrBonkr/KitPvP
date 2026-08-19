package com.planetgallium.kitpvp.game;

import java.util.ArrayList;
import java.util.List;

import com.planetgallium.kitpvp.util.Toolkit;
import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class Infoboard {

	/** Sidebar entries are keyed by a unique colour code, so there can never be more lines than there are codes. */
	private static final int MAX_LINES = Math.min(15, ChatColor.values().length);
	private static final int MAX_TITLE_LENGTH = 32;

	private final List<ScoreboardText> list;
	private final Scoreboard scoreBoard;
	private final Objective objective;
	private String tag;
	private int lastSentCount;

	public Infoboard(final Scoreboard scoreboard2, final String title) {
		this.list = new ArrayList<>();
		this.lastSentCount = -1;
		this.scoreBoard = scoreboard2;
		this.tag = truncateTitle(title);
		this.objective = getOrCreateObjective(this.tag);
		this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
	}

	/**
	 * Scoreboard titles are capped by the server at 32 characters. Going over used
	 * to throw and take the whole join or respawn handler with it, so the title is
	 * shortened and reported instead.
	 */
	private String truncateTitle(String title) {
		String coloured = ChatColor.translateAlternateColorCodes('&', title == null ? "" : title);

		if (coloured.length() > MAX_TITLE_LENGTH) {
			Toolkit.warnOnce("scoreboard-title:" + coloured,
					"&7[&b&lKIT-PVP&7] &eThe scoreboard title in scoreboard.yml is longer than " + MAX_TITLE_LENGTH +
					" characters (colour codes count); it has been shortened to fit.");
			coloured = coloured.substring(0, MAX_TITLE_LENGTH);
		}
		return coloured;
	}

	public void setTitle(final String title) {
		this.tag = truncateTitle(title);
		this.objective.setDisplayName(this.tag);
	}

	public void add(String input) {
		if (list.size() >= MAX_LINES) {
			Toolkit.warnOnce("scoreboard-lines",
					"&7[&b&lKIT-PVP&7] &eThe scoreboard in scoreboard.yml has more than " + MAX_LINES +
					" lines; the extra lines are not shown.");
			return;
		}

		input = ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);

		ScoreboardText text;

		// Team prefixes were limited to 16 characters before 1.13; newer servers can
		// take the whole line in one go, so it is only split where it has to be.
		if (Toolkit.versionToNumber() >= 113 || input.length() <= 16) {
			text = new ScoreboardText(input, "");
		} else {
			String first = input.substring(0, 16);
			String second = input.substring(16);
			if (first.endsWith(String.valueOf('§'))) {
				first = first.substring(0, first.length() - 1);
				second = '§' + second;
			}
			final String lastColors = ChatColor.getLastColors(first);
			second = lastColors + second;
			text = new ScoreboardText(first, StringUtils.left(second, 16));
		}
		this.list.add(text);
	}

	public void clear() {
		this.list.clear();
	}

	public void remove(final int index) {
		final String name = this.getNameForIndex(index);
		this.scoreBoard.resetScores(name);

		final Team team = this.scoreBoard.getTeam(teamNameForIndex(index));
		if (team != null) {
			team.unregister();
		}
	}

	public void update(final Player player) {
		if (player.getScoreboard() != this.scoreBoard) {
			player.setScoreboard(this.scoreBoard);
		}

		for (int sentCount = 0; sentCount < this.list.size(); ++sentCount) {
			final Team i = this.getOrCreateTeam(teamNameForIndex(sentCount), sentCount);
			final ScoreboardText str = this.list.get(this.list.size() - sentCount - 1);
			i.setPrefix(str.getLeft());
			i.setSuffix(str.getRight());
			this.objective.getScore(this.getNameForIndex(sentCount)).setScore(sentCount + 1);
		}

		if (this.lastSentCount != -1) {
			for (int extra = this.list.size(); extra < this.lastSentCount; extra++) {
				this.remove(extra);
			}
		}
		this.lastSentCount = this.list.size();
	}

	private String teamNameForIndex(final int index) {
		return ChatColor.stripColor(StringUtils.left(this.tag, 14)) + index;
	}

	public Team getOrCreateTeam(final String team, final int i) {
		Team value = this.scoreBoard.getTeam(team);
		if (value == null) {
			value = this.scoreBoard.registerNewTeam(team);
			value.addEntry(this.getNameForIndex(i));
		}
		return value;
	}

	public Objective getOrCreateObjective(final String objective) {
		Objective value = this.scoreBoard.getObjective("dummyhubobj");
		if (value == null) {
			value = this.scoreBoard.registerNewObjective("dummyhubobj", "dummy");
		}
		value.setDisplayName(objective);
		return value;
	}

	public String getNameForIndex(final int index) {
		return ChatColor.values()[index].toString() + ChatColor.RESET;
	}

	public Scoreboard getScoreboard() { return scoreBoard; }

	public void hide() {
		objective.setDisplaySlot(null);
	}

	private static class ScoreboardText {

		private final String left;
		private final String right;

		public ScoreboardText(final String left, final String right) {
			this.left = left;
			this.right = right;
		}

		public String getLeft() {
			return this.left;
		}

		public String getRight() {
			return this.right;
		}
	}

}
