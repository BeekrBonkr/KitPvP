package com.planetgallium.kitpvp.menu;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.api.Kit;
import com.planetgallium.kitpvp.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The kit selection menu. Items keep their configured slot within a page;
 * slot numbers greater than or equal to Menu.General.Size automatically
 * flow onto additional pages (slot 54 = page 2 slot 0, and so on, for a
 * size-54 menu). Navigation arrows are only added when more than one page
 * exists, so menus that fit in a single page look exactly as configured.
 *
 * Item names and lore are run through the placeholder system per viewer, so a
 * menu entry can show the level a kit needs, its remaining cooldown, or whether
 * the player can take it right now.
 */
public class KitMenu {

	private final Game plugin;
	private final Resources resources;

	public KitMenu(Game plugin, Resources resources) {
		this.plugin = plugin;
		this.resources = resources;
	}

	public void open(Player p) {
		open(p, 0);
	}

	public void open(Player p, int page) {
		Resource menuResource = resources.getMenu();
		int size = menuResource.getInt("Menu.General.Size");
		int pages = getPageCount();

		if (size <= 0 || size % 9 != 0 || size > 54) {
			Toolkit.warnOnce("menu-size",
					"&7[&b&lKIT-PVP&7] &cMenu.General.Size in menu.yml must be a multiple of 9 up to 54; " +
					"using 54 instead.");
			size = 54;
		}

		page = Math.max(0, Math.min(page, pages - 1));
		int pageStartSlot = page * size;

		ConfigurationSection section = menuResource.getConfigurationSection("Menu.Items");

		if (section == null) {
			p.sendMessage(resources.getMessages().fetchString("Messages.Error.MenuEmpty"));
			return;
		}

		// positions used by configured items on this page, to keep the
		// navigation arrows out of their way
		Set<Integer> occupiedPositions = new HashSet<>();
		for (String slotKey : section.getKeys(false)) {
			int virtualSlot = parseSlot(slotKey);
			if (virtualSlot >= pageStartSlot && virtualSlot < pageStartSlot + size) {
				occupiedPositions.add(virtualSlot - pageStartSlot);
			}
		}

		int previousPageSlot = -1;
		int nextPageSlot = -1;
		if (pages > 1) {
			if (page < pages - 1) {
				nextPageSlot = findNavigationSlot(occupiedPositions, size, size - 1);
				occupiedPositions.add(nextPageSlot);
			}
			if (page > 0) {
				previousPageSlot = findNavigationSlot(occupiedPositions, size, size - 2);
				occupiedPositions.add(previousPageSlot);
			}
		}

		String title = menuResource.fetchString("Menu.General.Title");
		if (pages > 1) {
			title += " &7(" + (page + 1) + "/" + pages + ")";
		}

		Menu menu = new Menu(title, new KitHolder(page, previousPageSlot, nextPageSlot), size);

		for (String slotKey : section.getKeys(false)) {
			int virtualSlot = parseSlot(slotKey);
			if (virtualSlot < pageStartSlot || virtualSlot >= pageStartSlot + size) {
				continue;
			}

			int position = virtualSlot - pageStartSlot;
			if (position == previousPageSlot || position == nextPageSlot) {
				// only happens when every slot of the page is configured
				Toolkit.warnOnce("menu-hidden:" + slotKey,
						"&7[&b&lKIT-PVP&7] &eMenu item at slot " + slotKey +
						" is hidden by a page navigation arrow; move it to a free slot.");
				continue;
			}

			String itemPath = "Menu.Items." + slotKey;
			String kitName = getKitNameForItem(menuResource, itemPath);

			String name = applyPlaceholders(p, menuResource.fetchString(itemPath + ".Name"), kitName);
			Material material = Toolkit.safeMaterial(menuResource.fetchString(itemPath + ".Material"));

			List<String> lore = new ArrayList<>();
			for (String line : menuResource.getStringList(itemPath + ".Lore")) {
				lore.add(applyPlaceholders(p, line, kitName));
			}

			menu.addItem(name, material, lore, position);
		}

		if (previousPageSlot != -1) {
			menu.addItem(resources.getMessages().fetchString("Messages.Other.MenuPreviousPage"),
					Toolkit.safeMaterial("ARROW"),
					Arrays.asList("&7Go to page " + page + "."), previousPageSlot);
		}
		if (nextPageSlot != -1) {
			menu.addItem(resources.getMessages().fetchString("Messages.Other.MenuNextPage"),
					Toolkit.safeMaterial("ARROW"),
					Arrays.asList("&7Go to page " + (page + 2) + "."), nextPageSlot);
		}

		menu.openMenu(p);
	}

	/**
	 * The kit a menu entry selects, read out of its left-click command so that
	 * kit-specific placeholders work without any extra configuration.
	 */
	private String getKitNameForItem(Resource menuResource, String itemPath) {
		for (String command : menuResource.getStringList(itemPath + ".Commands.Left-Click")) {
			String stripped = org.bukkit.ChatColor.stripColor(command);
			int kitIndex = stripped.toLowerCase().indexOf("kit ");

			if (kitIndex != -1) {
				String candidate = stripped.substring(kitIndex + 4).trim();
				int space = candidate.indexOf(' ');
				if (space != -1) {
					candidate = candidate.substring(0, space);
				}
				if (plugin.getArena().getKits().isKit(candidate)) {
					return plugin.getArena().getKits().resolveKitName(candidate);
				}
			}
		}
		return null;
	}

	/** Player placeholders everywhere, plus kit state on entries that point at a kit. */
	private String applyPlaceholders(Player p, String text, String kitName) {
		if (text == null) {
			return "";
		}

		if (kitName != null && (text.contains("%kit_") || text.contains("%kit%"))) {
			text = text.replace("%kit%", kitName);

			Kit kit = plugin.getArena().getKits().getKitByName(kitName);
			if (kit != null) {
				text = text.replace("%kit_level%", String.valueOf(kit.getLevel()))
						.replace("%kit_cooldown%", describeCooldown(p, kit))
						.replace("%kit_status%", describeStatus(p, kit));
			}
		}

		return plugin.getArena().getUtilities().addPlaceholdersIfPossible(p, text);
	}

	private String describeCooldown(Player p, Kit kit) {
		Cooldown remaining = plugin.getArena().getCooldowns().getRemainingCooldown(p, kit);

		if (remaining.toSeconds() <= 0) {
			return resources.getMenu().fetchString("Menu.General.Status.Ready", "&aReady");
		}
		return resources.getMenu().fetchString("Menu.General.Status.Cooldown", "&c%cooldown%")
				.replace("%cooldown%", remaining.formatted(true));
	}

	private String describeStatus(Player p, Kit kit) {
		if (kit.getPermission() != null && !kit.getPermission().isEmpty() && !p.hasPermission(kit.getPermission())) {
			return resources.getMenu().fetchString("Menu.General.Status.NoPermission", "&cLocked");
		}

		int level = plugin.getArena().getStats().getStat("level", p.getName());
		if (level < kit.getLevel() && Toolkit.getPermissionAmount(p, "kp.levelbypass.", 0) < kit.getLevel()) {
			return resources.getMenu().fetchString("Menu.General.Status.Locked", "&cRequires level %level%")
					.replace("%level%", String.valueOf(kit.getLevel()));
		}

		Cooldown remaining = plugin.getArena().getCooldowns().getRemainingCooldown(p, kit);
		if (remaining.toSeconds() > 0 && !p.hasPermission("kp.cooldownbypass")) {
			return resources.getMenu().fetchString("Menu.General.Status.Cooldown", "&c%cooldown%")
					.replace("%cooldown%", remaining.formatted(true));
		}

		return resources.getMenu().fetchString("Menu.General.Status.Available", "&aAvailable");
	}

	public int getPageCount() {
		Resource menuResource = resources.getMenu();
		int size = menuResource.getInt("Menu.General.Size");
		if (size <= 0 || size % 9 != 0 || size > 54) {
			size = 54;
		}

		ConfigurationSection section = menuResource.getConfigurationSection("Menu.Items");

		int highestSlot = 0;
		if (section != null) {
			for (String slotKey : section.getKeys(false)) {
				highestSlot = Math.max(highestSlot, parseSlot(slotKey));
			}
		}
		return highestSlot / size + 1;
	}

	private int parseSlot(String slotKey) {
		try {
			return Integer.parseInt(slotKey);
		} catch (NumberFormatException e) {
			Toolkit.warnOnce("menu-slot-key:" + slotKey,
					"&7[&b&lKIT-PVP&7] &cInvalid menu slot [" + slotKey + "] in menu.yml, ignoring.");
			return -1;
		}
	}

	/**
	 * Finds the closest free position at or below preferredPosition for a
	 * navigation arrow. Falls back to the preferred position (hiding the
	 * configured item there) if the entire page is full.
	 */
	private int findNavigationSlot(Set<Integer> occupiedPositions, int size, int preferredPosition) {
		for (int position = preferredPosition; position >= 0; position--) {
			if (!occupiedPositions.contains(position)) {
				return position;
			}
		}
		for (int position = preferredPosition + 1; position < size; position++) {
			if (!occupiedPositions.contains(position)) {
				return position;
			}
		}
		return preferredPosition;
	}

}
