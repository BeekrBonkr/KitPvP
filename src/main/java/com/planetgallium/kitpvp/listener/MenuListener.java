package com.planetgallium.kitpvp.listener;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.menu.KitHolder;
import com.planetgallium.kitpvp.menu.PreviewHolder;
import com.planetgallium.kitpvp.menu.PreviewMenu;
import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitRunnable;

public class MenuListener implements Listener {

    private final Game plugin;
    private final Arena arena;
    private final Resource config;
    private final Resource menuConfig;

    public MenuListener(Game plugin) {
        this.plugin = plugin;
        this.arena = plugin.getArena();
        this.config = plugin.getResources().getConfig();
        this.menuConfig = plugin.getResources().getMenu();
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }

        Player p = (Player) e.getWhoClicked();
        InventoryHolder holder = e.getView().getTopInventory().getHolder();

        if (!(holder instanceof KitHolder) && !(holder instanceof PreviewHolder)) {
            return;
        }

        // Nothing in either menu may be picked up, moved or shift-clicked out —
        // including the back arrow, which used to be the one clickable item that
        // was not cancelled and so could be carried away.
        e.setCancelled(true);

        if (e.getClickedInventory() == null || e.getClickedInventory().getHolder() != holder) {
            return; // a click in the player's own inventory while the menu is open
        }

        if (holder instanceof KitHolder) {
            handleKitMenuClick(e, p, (KitHolder) holder);
        } else if (e.getSlot() == PreviewMenu.BACK_ARROW_SLOT) {
            p.closeInventory();

            new BukkitRunnable() {
                @Override
                public void run() {
                    Toolkit.runCommands(p, config.getStringList("PreviewMenuBackArrowCommands"), "none", "none");
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    private void handleKitMenuClick(InventoryClickEvent e, Player p, KitHolder holder) {
        Inventory openMenu = e.getClickedInventory();
        int clickedSlot = e.getSlot();

        if (clickedSlot == holder.getPreviousPageSlot() || clickedSlot == holder.getNextPageSlot()) {
            int targetPage = holder.getPage() + (clickedSlot == holder.getNextPageSlot() ? 1 : -1);

            new BukkitRunnable() {
                @Override
                public void run() {
                    arena.getMenus().getKitMenu().open(p, targetPage);
                }
            }.runTaskLater(plugin, 1L);
            return;
        }

        int menuSize = openMenu.getSize();
        String itemPath = "Menu.Items." + (holder.getPage() * menuSize + clickedSlot);

        if (!menuConfig.contains(itemPath) || openMenu.getItem(clickedSlot) == null) {
            return;
        }

        String clickType = clickTypeFor(e.getClick());
        if (clickType == null) {
            return; // middle click, number keys, double click: not a selection
        }

        // The menu deliberately stays open: a kit that is refused for level or
        // cooldown leaves the player where they were, free to pick another.
        // Kits.attemptToGiveKitToPlayer closes it once a kit is actually given.
        new BukkitRunnable() {
            @Override
            public void run() {
                Toolkit.runCommands(p, menuConfig.getStringList(itemPath + ".Commands." + clickType),
                        "none", "none");
            }
        }.runTaskLater(plugin, 1L);
    }

    /** Shift-clicking a kit is still a left click, and the middle button is neither. */
    private String clickTypeFor(ClickType click) {
        switch (click) {
            case LEFT:
            case SHIFT_LEFT:
                return "Left-Click";
            case RIGHT:
            case SHIFT_RIGHT:
                return "Right-Click";
            default:
                return null;
        }
    }

    /** Dragging across a menu is a click by another name, and was previously unguarded. */
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();

        if (holder instanceof KitHolder || holder instanceof PreviewHolder) {
            int topSize = e.getView().getTopInventory().getSize();

            for (int rawSlot : e.getRawSlots()) {
                if (rawSlot < topSize) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

}
