package com.planetgallium.kitpvp.menu;

import java.util.ArrayList;
import java.util.List;

import com.cryptomorin.xseries.XMaterial;
import com.planetgallium.kitpvp.api.Kit;
import com.planetgallium.kitpvp.util.Resources;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.entity.Player;

import com.planetgallium.kitpvp.util.Menu;
import org.bukkit.potion.PotionEffect;

public class PreviewMenu {

	/** Slot of the arrow that takes the player back to the kit menu. */
	public static final int BACK_ARROW_SLOT = 8;

	/**
	 * Builds the preview fresh for each viewer. It used to be cached and shared,
	 * which meant anything taken out of it stayed missing for everyone.
	 */
	private Menu create(Kit kit, Resources resources) {
		String previewMenuTitle = resources.getMessages().fetchString("Messages.Other.PreviewMenuTitle")
				.replace("%kit%", kit.getName());
		Menu previewMenu = new Menu(previewMenuTitle, new PreviewHolder(), 54);

		//			ARMOR			//

		if (kit.getHelmet() != null)
			previewMenu.setItem(kit.getHelmet(), 0);

		if (kit.getChestplate() != null)
			previewMenu.setItem(kit.getChestplate(), 1);

		if (kit.getLeggings() != null)
			previewMenu.setItem(kit.getLeggings(), 2);

		if (kit.getBoots() != null)
			previewMenu.setItem(kit.getBoots(), 3);

		//		POTION EFFECTS		//

		List<String> effectsLore = new ArrayList<>();

		for (PotionEffect effect : kit.getEffects()) {
			String type = effect.getType().getName();
			int amplifierNonZeroBased = effect.getAmplifier() + 1;
			int durationSeconds = effect.getDuration() / 20;

			effectsLore.add("&7- " + type + " " + amplifierNonZeroBased + " (" +
					(effect.getDuration() < 0 || durationSeconds > 10000 ? "Infinite" : (durationSeconds + "s")) + ")");
		}

		if (kit.getEffects().size() == 0) {
			effectsLore.add("&7None");
		}

		String menuPotionEffectsItemName =
				resources.getMessages().fetchString("Messages.Other.PreviewMenuPotionEffectsItemName");
		previewMenu.addItem(menuPotionEffectsItemName, XMaterial.BREWING_STAND.parseMaterial(), effectsLore, 4);

		//			OFFHAND			//

		if (!Toolkit.isEmpty(kit.getOffhand())) {
			previewMenu.setItem(kit.getOffhand(), 5);
		}

		//			HOTBAR			//

		for (int i = 0; i < 9; i++) {
			if (kit.getInventory().containsKey(i)) {
				previewMenu.setItem(kit.getInventory().get(i), (45 + i));
			}
		}

		//			ITEMS			//

		for (int i = 9; i < 36; i++) {
			if (kit.getInventory().containsKey(i)) {
				previewMenu.setItem(kit.getInventory().get(i), (9 + i));
			}
		}

		//			FILL			//

		if (kit.getFill() != null) {
			for (int i = 18; i < 54; i++) {
				if (previewMenu.getSlot(i) == null) {
					previewMenu.setItem(kit.getFill(), i);
				}
			}
		}

		String menuBackArrowItemName =
				resources.getMessages().fetchString("Messages.Other.PreviewMenuBackArrowItemName");
		previewMenu.addItem(menuBackArrowItemName, XMaterial.ARROW.parseMaterial(), new ArrayList<>(),
				BACK_ARROW_SLOT);

		return previewMenu;
	}

	public void open(Player p, Kit kit, Resources resources) {
		create(kit, resources).openMenu(p);
	}

}
