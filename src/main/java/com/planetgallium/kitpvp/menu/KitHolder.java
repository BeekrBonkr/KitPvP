package com.planetgallium.kitpvp.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class KitHolder implements InventoryHolder {

	private final int page;
	private final int previousPageSlot;
	private final int nextPageSlot;

	public KitHolder() {
		this(0, -1, -1);
	}

	public KitHolder(int page, int previousPageSlot, int nextPageSlot) {
		this.page = page;
		this.previousPageSlot = previousPageSlot;
		this.nextPageSlot = nextPageSlot;
	}

	public int getPage() { return page; }

	/** Slot of the previous-page arrow in this inventory, or -1 if none */
	public int getPreviousPageSlot() { return previousPageSlot; }

	/** Slot of the next-page arrow in this inventory, or -1 if none */
	public int getNextPageSlot() { return nextPageSlot; }

	@Override
	public Inventory getInventory() { return null; }

}
