package com.planetgallium.kitpvp.menu;

import com.planetgallium.kitpvp.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

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
 */
public class KitMenu {

	private final Resources resources;

	public KitMenu(Resources resources) {
		this.resources = resources;
	}

	public void open(Player p) {
		open(p, 0);
	}

	public void open(Player p, int page) {
		Resource menuResource = resources.getMenu();
		int size = menuResource.getInt("Menu.General.Size");
		int pages = getPageCount();

		page = Math.max(0, Math.min(page, pages - 1));
		int pageStartSlot = page * size;

		ConfigurationSection section = menuResource.getConfigurationSection("Menu.Items");

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
				Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &eMenu item at slot " + slotKey +
						" is hidden by a page navigation arrow; move it to a free slot.");
				continue;
			}

			String itemPath = "Menu.Items." + slotKey;
			String name = menuResource.fetchString(itemPath + ".Name");
			Material material = Toolkit.safeMaterial(menuResource.fetchString(itemPath + ".Material"));
			List<String> lore = menuResource.getStringList(itemPath + ".Lore");

			menu.addItem(name, material, lore, position);
		}

		if (previousPageSlot != -1) {
			menu.addItem("&a&lPrevious Page", Toolkit.safeMaterial("ARROW"),
					Arrays.asList("&7Go to page " + page + "."), previousPageSlot);
		}
		if (nextPageSlot != -1) {
			menu.addItem("&a&lNext Page", Toolkit.safeMaterial("ARROW"),
					Arrays.asList("&7Go to page " + (page + 2) + "."), nextPageSlot);
		}

		menu.openMenu(p);
	}

	public int getPageCount() {
		Resource menuResource = resources.getMenu();
		int size = menuResource.getInt("Menu.General.Size");
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
			Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cInvalid menu slot [" + slotKey + "] in menu.yml, ignoring.");
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
