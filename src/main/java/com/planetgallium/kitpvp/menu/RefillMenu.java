package com.planetgallium.kitpvp.menu;

import com.planetgallium.kitpvp.util.Menu;
import com.planetgallium.kitpvp.util.Resources;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.entity.Player;

public class RefillMenu {

    private final Resources resources;

    public RefillMenu(Resources resources) {
        this.resources = resources;
    }

    /** A fresh inventory per open, so what one player takes does not disappear for the next. */
    public void open(Player p) {
        Menu menu = new Menu(resources.getConfig().fetchString("Soups.RefillMenuTitle", "Refill"),
                new RefillHolder(), 54);

        for (int i = 0; i < menu.getSize(); i++) {
            menu.addItem(resources.getConfig().fetchString("Soups.Name"),
                    Toolkit.safeMaterial("MUSHROOM_STEW"),
                    resources.getConfig().getStringList("Soups.Lore"), i);
        }

        menu.openMenu(p);
    }

}
