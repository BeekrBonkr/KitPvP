package com.planetgallium.kitpvp.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks the soup refill inventory, which players are meant to take items out of. */
public class RefillHolder implements InventoryHolder {

	@Override
	public Inventory getInventory() { return null; }

}
