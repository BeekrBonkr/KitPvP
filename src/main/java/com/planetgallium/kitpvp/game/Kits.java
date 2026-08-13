package com.planetgallium.kitpvp.game;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XPotion;
import com.cryptomorin.xseries.XSound;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.api.Ability;
import com.planetgallium.kitpvp.api.Kit;
import com.planetgallium.kitpvp.api.PlayerSelectKitEvent;
import com.planetgallium.kitpvp.item.AttributeParser;
import com.planetgallium.kitpvp.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class Kits {

    private final Game plugin;
    private final Arena arena;
    private final Resources resources;
    private final Resource messages;

    private final Map<String, String> playerKits;

    public Kits(Game plugin, Arena arena) {
        this.arena = arena;
        this.plugin = plugin;
        this.resources = plugin.getResources();
        this.messages = resources.getMessages();

        this.playerKits = new HashMap<>();
    }

    // Could also load and cache kits onEnable like Abilities.yml so no lag spike for hefty kit when first used by
    // player

    public void createKit(Player fromPlayer, String kitName) {
        Kit kitToCreate = createKitFromPlayer(fromPlayer, kitName);

        Resource kitResource = new Resource(plugin, "kits/" + kitToCreate.getName() + ".yml");
        kitToCreate.toResource(kitResource);

        resources.addResource(kitToCreate.getName() + ".yml", kitResource);
        plugin.getDatabase().addKitCooldownTable(kitName);

        automaticallyAddKitToMenu(fromPlayer, kitToCreate);
    }

    private void automaticallyAddKitToMenu(Player fromPlayer, Kit kitToCreate) {
        if (plugin.getConfig().getBoolean("Other.AutomaticallyAddKitToMenu")) {
            if (!addKitToMenu(kitToCreate.getName())) {
                fromPlayer.sendMessage(messages.fetchString("Messages.Error.Menu"));
            }
        }
    }

    /**
     * Writes a menu entry for the kit at the next free slot. The entry's
     * appearance comes from the optional Menu section of the kit file
     * (Menu.Name, Menu.Material, Menu.Lore); otherwise the kit's first
     * hotbar item is used as the icon. Returns false if the menu is full.
     */
    private boolean addKitToMenu(String kitName) {
        Resource menuConfig = resources.getMenu();
        int menuSize = menuConfig.getInt("Menu.General.Size");

        int slot = findNextFreeMenuSlot(menuConfig, menuSize);
        if (slot == -1) {
            return false;
        }

        Resource kitResource = resources.getKit(kitName);

        String name = "&a&l" + kitName + " Kit";
        String material = "BEDROCK";
        List<String> lore = new ArrayList<>(Arrays.asList(
                "&eLeft-click to select.",
                "&eRight-click to preview."));

        if (kitResource != null) {
            if (kitResource.contains("Menu.Name")) {
                name = kitResource.getString("Menu.Name");
            }
            if (kitResource.contains("Menu.Material")) {
                material = kitResource.getString("Menu.Material");
            } else if (kitResource.contains("Inventory.Items.0.Material")) {
                material = kitResource.getString("Inventory.Items.0.Material");
            }
            if (kitResource.contains("Menu.Lore")) {
                lore = kitResource.getStringList("Menu.Lore");
            }
        }

        String pathPrefix = "Menu.Items." + slot;
        menuConfig.set(pathPrefix + ".Name", name);
        menuConfig.set(pathPrefix + ".Material", material);
        menuConfig.set(pathPrefix + ".Lore", lore);
        menuConfig.set(pathPrefix + ".Commands.Left-Click",
                new String[]{"player: kp kit " + kitName});
        menuConfig.set(pathPrefix + ".Commands.Right-Click",
                new String[]{"player: kp preview " + kitName});

        menuConfig.save();
        menuConfig.load();
        return true;
    }

    /**
     * Next unconfigured virtual slot, skipping the last two positions of
     * each page which are where the pagination arrows go (capped at 10
     * pages, see KitMenu).
     */
    private int findNextFreeMenuSlot(Resource menuConfig, int menuSize) {
        for (int slot = 0; slot < menuSize * 10; slot++) {
            int positionInPage = slot % menuSize;
            if (positionInPage == menuSize - 1 || positionInPage == menuSize - 2) {
                continue;
            }
            if (!menuConfig.contains("Menu.Items." + slot)) {
                return slot;
            }
        }
        return -1;
    }

    public Kit createKitFromPlayer(Player player, String name) {
        Player p = player;
        
        //          KIT             //

        Kit kit = new Kit(name);

        kit.setHelmet(p.getInventory().getHelmet());
        kit.setChestplate(p.getInventory().getChestplate());
        kit.setLeggings(p.getInventory().getLeggings());
        kit.setBoots(p.getInventory().getBoots());

        kit.setMaxHealth(Toolkit.getMaxHealth(p));

        for (PotionEffect effect : p.getActivePotionEffects()) {
            PotionEffectType type = effect.getType();
            int amplifier = effect.getAmplifier();
            int duration = effect.getDuration();
            int amplifierNonZeroBased = amplifier + 1;
            int durationSeconds = duration / 20;

            kit.addEffect(type, amplifierNonZeroBased, durationSeconds);
        }

        for (int i = 0; i < 36; i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item == null) {
                continue; // skip this item
            }

            if (Toolkit.hasMatchingMaterial(item, "MUSHROOM_STEW")) {
                ItemMeta itemMeta = item.getItemMeta();
                itemMeta.setDisplayName(resources.getConfig().fetchString("Soups.Name"));
                itemMeta.setLore(Toolkit.colorizeList(resources.getConfig().getStringList("Soups.Lore")));
                item.setItemMeta(itemMeta);
            }

            kit.setInventoryItem(i, item);
        }
        
        if (Toolkit.versionToNumber() >= 19) {
            ItemStack offhandItem = p.getInventory().getItemInOffHand();
            if (offhandItem != null) {
                kit.setOffhand(offhandItem);
            }
        }

        //          ABILITY         //

        Ability sampleAbility = new Ability(kit.getName() + "-Blank");

        ItemStack activator = XMaterial.EMERALD.parseItem();
        ItemMeta activatorMeta = activator.getItemMeta();

        activatorMeta.setDisplayName(Toolkit.translate("&aBlank Kit Ability &7(Right Click)"));
        activator.setItemMeta(activatorMeta);

        sampleAbility.setActivator(activator);

        sampleAbility.setMessage("%prefix% &7You have used your ability.");
        sampleAbility.setSound(XSound.BLOCK_NOTE_BLOCK_PLING.parseSound(), 1, 1);
        sampleAbility.addEffect(XPotion.SPEED.getPotionEffectType(), 1, 10);
        sampleAbility.addCommand("console: This command is run from the console, you can use %player%");
        sampleAbility.addCommand("player: This command is run from the player, you can use %player%");

        resources.addAbilityResource(sampleAbility);

        return kit;
    }

    private Kit createKitFromResource(Resource resource) throws IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        Kit kit = new Kit(trimName(resource.getName()));

        kit.setPermission(resource.fetchString("Kit.Permission"));
        kit.setLevel(resource.getInt("Kit.Level"));
        kit.setCooldown(new Cooldown(resource.fetchString("Kit.Cooldown")));

        kit.setMaxHealth(resource.contains("Kit.Health") ? resource.getInt("Kit.Health") : 20);

        if (resource.contains("Kit.MinimumVersion")) {
            kit.setMinimumVersion(String.valueOf(resource.get("Kit.MinimumVersion")));
        }
        kit.setUnsupportedMaterial(findUnsupportedMaterial(resource));

        kit.setHelmet(AttributeParser.getItemStackFromPath(resource, "Inventory.Armor.Helmet"));
        kit.setChestplate(AttributeParser.getItemStackFromPath(resource, "Inventory.Armor.Chestplate"));
        kit.setLeggings(AttributeParser.getItemStackFromPath(resource, "Inventory.Armor.Leggings"));
        kit.setBoots(AttributeParser.getItemStackFromPath(resource, "Inventory.Armor.Boots"));

        for (PotionEffect effect : AttributeParser.getEffectsFromPath(resource, "Effects")) {
            kit.addEffect(effect.getType(), effect.getAmplifier(), effect.getDuration());
        }

        for (int i = 0; i < 36; i++) {
            if (resource.contains("Inventory.Items." + i)) {
                kit.setInventoryItem(i, AttributeParser.getItemStackFromPath(resource, "Inventory.Items." + i));
            }
        }

        kit.setFill(AttributeParser.getItemStackFromPath(resource, "Inventory.Items.Fill"));
        kit.setOffhand(AttributeParser.getItemStackFromPath(resource, "Inventory.Items.Offhand"));

        kit.setOption("AddOverflowItemsOnKit",
                resources.getConfig().getBoolean("Arena.AddOverflowItemsOnKit"));
        kit.setOption("DropRemainingOverflowItemsOnKit",
                resources.getConfig().getBoolean("Arena.DropRemainingOverflowItemsOnKit"));
        kit.setOption("Message-OverflowItemsLost",
                resources.getMessages().fetchString("Messages.Error.OverflowItemsLost"));
        kit.setOption("Message-OverflowItemsDropped",
                resources.getMessages().fetchString("Messages.Other.OverflowItemsDropped"));

        return kit;
    }

    public void attemptToGiveKitToPlayer(Player player, Kit kit) {
        Player p = player;

        if (kit == null) {
            p.sendMessage(messages.fetchString("Messages.Error.Lost"));
            return;
        }

        if (!kit.meetsVersionRequirement()) {
            p.sendMessage(messages.fetchString("Messages.Error.KitVersion")
                    .replace("%version%", kit.getMinimumVersion()));
            return;
        }

        if (kit.getUnsupportedMaterial() != null) {
            p.sendMessage(messages.fetchString("Messages.Error.KitUnsupported"));
            Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cKit " + kit.getName() + " is disabled: material " +
                    kit.getUnsupportedMaterial() + " does not exist on this server version.");
            return;
        }

        if (!p.hasPermission(kit.getPermission())) {
            p.sendMessage(messages.fetchString("Messages.General.Permission")
                    .replace("%permission%", kit.getPermission()));
            return;
        }

        if (!(Toolkit.getPermissionAmount(p, "kp.levelbypass.", 0) >= kit.getLevel() ||
                arena.getStats().getStat("level", p.getName()) >= kit.getLevel())) {
            p.sendMessage(messages.fetchString("Messages.Other.Needed")
                    .replace("%level%", String.valueOf(kit.getLevel())));
            return;
        }

        Cooldown cooldownRemaining = arena.getCooldowns().getRemainingCooldown(p, kit);
        if (!p.hasPermission("kp.cooldownbypass") && cooldownRemaining.toSeconds() > 0) {
            p.sendMessage(messages.fetchString("Messages.Error.CooldownKit")
                    .replace("%cooldown%", cooldownRemaining.formatted(false)));
            return;
        }

        if (playerHasKit(player.getName())) {
            p.sendMessage(messages.fetchString("Messages.Error.Selected"));
            Toolkit.playSoundToPlayer(p, "ENTITY_ENDER_DRAGON_HURT", 1);
            return;
        }

        if (resources.getConfig().getBoolean("Arena.ClearInventoryOnKit")) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
        }

        kit.apply(p);
        p.sendMessage(messages.fetchString("Messages.Commands.Kit").replace("%kit%", kit.getName()));
        Toolkit.playSoundToPlayer(p, "ENTITY_HORSE_ARMOR", 1);

        Bukkit.getPluginManager().callEvent(new PlayerSelectKitEvent(player, kit));
        setPlayerKit(p.getName(), kit.getName());

        Cooldown kitCooldown = kit.getCooldown();
        if (kitCooldown != null && kitCooldown.toSeconds() > 0 && !p.hasPermission("kp.cooldownbypass")) {
            arena.getCooldowns().setKitCooldown(p.getName(), kit.getName());
        }

        Resource kitResource = resources.getKit(kit.getName());
        if (kitResource != null && kitResource.contains("Commands")) {
            Toolkit.runCommands(p, kitResource.getStringList("Commands"),
                    "none", "none");
        }
    }

    /**
     * Scans every Material key in a kit file and returns the first material
     * name that does not exist on the running server version, or null if
     * all materials are supported.
     */
    private String findUnsupportedMaterial(Resource resource) {
        for (String key : resource.getKeys(true)) {
            if (key.endsWith("Material")) {
                String materialName = resource.fetchString(key);
                if (!Toolkit.isMaterialSupported(materialName)) {
                    return materialName;
                }
            }
        }
        return null;
    }

    /**
     * Called on startup to warn the console about kits that will be
     * disabled on this server version, instead of failing when a player
     * first tries to use one.
     */
    public void validateLoadedKits() {
        int serverVersion = Toolkit.versionToNumber();

        for (Resource kitResource : resources.getKitResources()) {
            String kitName = trimName(kitResource.getName());

            if (kitResource.contains("Kit.MinimumVersion")) {
                String minimumVersion = String.valueOf(kitResource.get("Kit.MinimumVersion"));
                if (serverVersion < Toolkit.versionStringToNumber(minimumVersion)) {
                    Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &eKit " + kitName + " is disabled: it requires " +
                            "server version " + minimumVersion + " or newer.");
                    continue;
                }
            }

            String unsupportedMaterial = findUnsupportedMaterial(kitResource);
            if (unsupportedMaterial != null) {
                Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &eKit " + kitName + " is disabled: material " +
                        unsupportedMaterial + " does not exist on this server version.");
            }
        }
    }

    private Kit loadKitFromCacheOrCreate(String kitName) {
        if (!CacheManager.getKitCache().containsKey(kitName)) {
            if (isKit(kitName)) {
                Resource kit = resources.getKit(kitName);
                try {
                    CacheManager.getKitCache().put(kitName, createKitFromResource(kit));
                } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            } else {
                Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cNo kit with name " + kitName + " found in the kits folder." +
                        " Try reloading the server.");
            }
        }
        return CacheManager.getKitCache().get(kitName);
    }

    public Kit getKitByName(String kitName) {
        return loadKitFromCacheOrCreate(kitName);
    }

    public Kit getKitOfPlayer(String playerName) {
        String kitName = playerKits.get(playerName);
        if (kitName == null) {
            return null;
        }
        return loadKitFromCacheOrCreate(kitName);
    }

    public boolean isKit(String kitName) {
        return resources.getPluginDirectoryFiles("kits", false).contains(kitName);
    }

    public void deleteKit(String kitName) {
        resources.removeResource(kitName + ".yml");
        plugin.getDatabase().deleteKitCooldownTable(kitName);
        CacheManager.getKitCache().remove(kitName);
    }

    public void setPlayerKit(String playerName, String kitName) {
        playerKits.put(playerName, kitName);
    }

    public boolean playerHasKit(String playerName) {
        return playerKits.containsKey(playerName);
    }

    public void resetPlayerKit(String playerName) {
        playerKits.remove(playerName);
    }

    private String trimName(String kitNameWithFileEnding) {
        String[] splitName = kitNameWithFileEnding.split(".yml");
        return splitName[0];
    }

}
