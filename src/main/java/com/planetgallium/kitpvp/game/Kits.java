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

    public void createKit(Player fromPlayer, String kitName) {
        Kit kitToCreate = createKitFromPlayer(fromPlayer, kitName);

        Resource kitResource = new Resource(plugin, "kits/" + kitToCreate.getName() + ".yml");
        kitToCreate.toResource(kitResource);

        resources.addResource(kitToCreate.getName() + ".yml", kitResource);
        plugin.getDatabase().addKitCooldownTable(kitName);

        automaticallyAddKitToMenu(fromPlayer, kitToCreate);
    }

    private void automaticallyAddKitToMenu(Player fromPlayer, Kit kitToCreate) {
        if (resources.getConfig().getBoolean("Other.AutomaticallyAddKitToMenu")) {
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
            // read raw, so what is written back into menu.yml keeps its & colour codes
            if (kitResource.contains("Menu.Name")) {
                name = kitResource.fetchStringOrNull("Menu.Name");
            }
            if (kitResource.contains("Menu.Material")) {
                material = kitResource.fetchStringOrNull("Menu.Material");
            } else if (kitResource.contains("Inventory.Items.0.Material")) {
                material = kitResource.fetchStringOrNull("Inventory.Items.0.Material");
            }
            if (kitResource.contains("Menu.Lore")) {
                lore = kitResource.getRawStringList("Menu.Lore");
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
        if (menuSize <= 2) {
            return -1;
        }

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

        kit.setMaxHealth((int) Toolkit.getMaxHealth(p));

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
                if (itemMeta != null) {
                    itemMeta.setDisplayName(resources.getConfig().fetchString("Soups.Name"));
                    itemMeta.setLore(Toolkit.colorizeList(resources.getConfig().getStringList("Soups.Lore")));
                    item.setItemMeta(itemMeta);
                }
            }

            kit.setInventoryItem(i, item);
        }

        if (Toolkit.versionToNumber() >= 19) {
            ItemStack offhandItem = p.getInventory().getItemInOffHand();
            if (!Toolkit.isEmpty(offhandItem)) {
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
        String kitName = Resources.trimYamlSuffix(resource.getName());
        Kit kit = new Kit(kitName);

        // a kit file with no permission is open to everyone rather than gated
        // behind a node nobody can grant
        kit.setPermission(resource.contains("Kit.Permission") ?
                resource.fetchString("Kit.Permission") : "kp.kit." + kitName);
        kit.setLevel(resource.getInt("Kit.Level"));
        kit.setCooldown(new Cooldown(resource.contains("Kit.Cooldown") ?
                String.valueOf(resource.get("Kit.Cooldown")) : "0"));

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

    /** Returns true when the player actually walked away with the kit. */
    public boolean attemptToGiveKitToPlayer(Player player, Kit kit) {
        Player p = player;

        if (kit == null) {
            p.sendMessage(messages.fetchString("Messages.Error.Lost"));
            return false;
        }

        if (!kit.meetsVersionRequirement()) {
            p.sendMessage(messages.fetchString("Messages.Error.KitVersion")
                    .replace("%version%", kit.getMinimumVersion()));
            return false;
        }

        if (kit.getUnsupportedMaterial() != null) {
            p.sendMessage(messages.fetchString("Messages.Error.KitUnsupported"));
            Toolkit.warnOnce("kit-unsupported:" + kit.getName(),
                    "&7[&b&lKIT-PVP&7] &cKit " + kit.getName() + " is disabled: material " +
                    kit.getUnsupportedMaterial() + " does not exist on this server version.");
            return false;
        }

        if (kit.getPermission() != null && !kit.getPermission().isEmpty() && !p.hasPermission(kit.getPermission())) {
            p.sendMessage(messages.fetchString("Messages.General.Permission")
                    .replace("%permission%", kit.getPermission()));
            return false;
        }

        if (!(Toolkit.getPermissionAmount(p, "kp.levelbypass.", 0) >= kit.getLevel() ||
                arena.getStats().getStat("level", p.getName()) >= kit.getLevel())) {
            p.sendMessage(messages.fetchString("Messages.Other.Needed")
                    .replace("%level%", String.valueOf(kit.getLevel())));
            return false;
        }

        Cooldown cooldownRemaining = arena.getCooldowns().getRemainingCooldown(p, kit);
        if (!p.hasPermission("kp.cooldownbypass") && cooldownRemaining.toSeconds() > 0) {
            p.sendMessage(messages.fetchString("Messages.Error.CooldownKit")
                    .replace("%cooldown%", cooldownRemaining.formatted(false)));
            return false;
        }

        if (playerHasKit(player.getName())) {
            p.sendMessage(messages.fetchString("Messages.Error.Selected"));
            Toolkit.playSoundToPlayer(p, "ENTITY_ENDER_DRAGON_HURT", 1);
            return false;
        }

        // swapping kits mid-fight is the same escape as clearing one
        if (!p.hasPermission("kp.combattag.bypass") && plugin.getCombatTagger().denyIfTagged(p)) {
            return false;
        }

        if (resources.getConfig().getBoolean("Arena.ClearInventoryOnKit")) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
        }

        kit.apply(p);
        p.closeInventory(); // the kit menu closes once a kit is actually handed over, not before
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

        return true;
    }

    /**
     * Scans every Material key in a kit file and returns the first material
     * name that does not exist on the running server version, or null if
     * all materials are supported.
     */
    private String findUnsupportedMaterial(Resource resource) {
        for (String key : resource.getKeys(true)) {
            if (key.endsWith("Material")) {
                String materialName = resource.fetchStringOrNull(key);
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
            String kitName = Resources.trimYamlSuffix(kitResource.getName());

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
        String resolvedName = resources.resolveKitName(kitName);

        if (resolvedName == null) {
            return null;
        }

        Kit cachedKit = CacheManager.getKitCache().get(resolvedName);
        if (cachedKit != null) {
            return cachedKit;
        }

        Resource kitResource = resources.getKit(resolvedName);
        if (kitResource == null) {
            return null;
        }

        try {
            Kit loadedKit = createKitFromResource(kitResource);
            CacheManager.getKitCache().put(resolvedName, loadedKit);
            return loadedKit;
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &cCould not read kit " + resolvedName + ": " + e.getMessage());
            return null;
        }
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

    /** Kit names are matched without regard to case, so "/kp kit fighter" works. */
    public boolean isKit(String kitName) {
        return resources.resolveKitName(kitName) != null;
    }

    public String resolveKitName(String kitName) {
        return resources.resolveKitName(kitName);
    }

    public List<String> getKitNames() {
        return resources.getKitNames();
    }

    public void deleteKit(String kitName) {
        String resolvedName = resources.resolveKitName(kitName);
        if (resolvedName == null) {
            return;
        }

        resources.removeResource(resolvedName + ".yml");
        plugin.getDatabase().deleteKitCooldownTable(resolvedName);
        CacheManager.getKitCache().remove(resolvedName);
        playerKits.values().removeIf(resolvedName::equals);
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

}
