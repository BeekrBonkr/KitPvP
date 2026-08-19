package com.planetgallium.kitpvp.util;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class Menu {

	/** Inventory titles longer than this are rejected outright by servers before 1.13. */
	private static final int LEGACY_MAX_TITLE_LENGTH = 32;

	private final String title;
	private final int size;
	private final Inventory menu;
	private final InventoryHolder owner;

	public Menu(String title, InventoryHolder owner, int size) {
		String colouredTitle = Toolkit.translate(title);

		if (Toolkit.versionToNumber() < 113 && colouredTitle.length() > LEGACY_MAX_TITLE_LENGTH) {
			Toolkit.warnOnce("menu-title:" + colouredTitle,
					"&7[&b&lKIT-PVP&7] &eThe menu title \"" + colouredTitle + "\" is longer than " +
					LEGACY_MAX_TITLE_LENGTH + " characters, which this server version cannot show; " +
					"it has been shortened.");
			colouredTitle = colouredTitle.substring(0, LEGACY_MAX_TITLE_LENGTH);
		}

		this.menu = Bukkit.createInventory(owner, size, colouredTitle);
		this.title = title;
		this.size = size;
		this.owner = owner;
	}

	public void addItem(String name, Material material, List<String> lore, int slot) {
		addItem(name, material, lore, 1, slot);
	}

	public void addItem(String name, Material material, List<String> lore, int amount, int slot) {
		if (slot < 0 || slot >= size) {
			Toolkit.warnOnce("menu-slot:" + name + ":" + slot,
					"&7[&b&lKIT-PVP&7] &cMenu slot " + slot + " is outside this menu (0-" + (size - 1) + ").");
			return;
		}

		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();

		if (meta != null) {
			meta.setDisplayName(Toolkit.translate(name));
			meta.setLore(Toolkit.colorizeList(lore));
			item.setItemMeta(meta);
		}

		item.setAmount(amount > 0 ? amount : 1);

		menu.setItem(slot, item);
	}

	public void setItem(ItemStack item, int slot) {
		if (slot >= 0 && slot < size) {
			menu.setItem(slot, item);
		}
	}

	public void openMenu(Player p) {
		p.openInventory(menu);
	}

	public void closeMenu(Player p) {
		p.closeInventory();
	}

	public ItemStack getSlot(int slot) { return menu.getItem(slot); }

	public String getTitle() { return title; }

	public InventoryHolder getOwner() { return owner; }

	public int getSize() { return size; }

}
