package com.planetgallium.kitpvp.api;

import com.planetgallium.kitpvp.item.AttributeWriter;
import com.planetgallium.kitpvp.util.Cooldown;
import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.PlayerInventory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Kit {

    private final String name;
    private String permission;
    private Cooldown cooldown;
    private int level;
    private int maxHealth = 20;
    private String minimumVersion;
    private String unsupportedMaterial;

    private final Map<String, Object> options;
    private final Map<Integer, ItemStack> inventory;
    private final List<PotionEffect> effects;

    private ItemStack kitHelmet;
    private ItemStack kitChestplate;
    private ItemStack kitLeggings;
    private ItemStack kitBoots;
    private ItemStack offhand;
    private ItemStack fill;

    public Kit(String name) {
        this.name = name;
        this.options = new HashMap<>();
        this.inventory = new HashMap<>();
        this.effects = new ArrayList<>();
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public void setCooldown(Cooldown cooldown) {
        this.cooldown = cooldown;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setMaxHealth(int health) {
        this.maxHealth = health;
    }

    public void setMinimumVersion(String minimumVersion) {
        this.minimumVersion = minimumVersion;
    }

    public void setUnsupportedMaterial(String unsupportedMaterial) {
        this.unsupportedMaterial = unsupportedMaterial;
    }

    /**
     * Returns true if the running server version meets this kit's
     * Kit.MinimumVersion requirement (always true when none is set).
     */
    public boolean meetsVersionRequirement() {
        if (minimumVersion == null) {
            return true;
        }

        int required = Toolkit.versionStringToNumber(minimumVersion);
        return required == -1 || Toolkit.versionToNumber() >= required;
    }

    public void setOption(String key, Object value) {
        // NOTE: using this strategy to pass data rather than using Resources class so
        // kit class can be used externally without needing access to Resources
        this.options.put(key, value);
    }

    /** Options are optional: a kit built through the API and applied directly still works. */
    private boolean booleanOption(String key, boolean fallback) {
        Object value = options.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private String stringOption(String key, String fallback) {
        Object value = options.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    public void setInventoryItem(int slot, ItemStack item) {
        inventory.put(slot, item);
    }

    public void addEffect(PotionEffectType type, int amplifierNonZeroBased, int durationSeconds) {
        PotionEffect effect = new PotionEffect(type, Toolkit.parsePotionEffectDuration(durationSeconds),
                Math.max(0, amplifierNonZeroBased - 1));
        effects.add(effect);
    }

    public void setHelmet(ItemStack helmet) {
        this.kitHelmet = helmet;
    }

    public void setChestplate(ItemStack chestplate) {
        this.kitChestplate = chestplate;
    }

    public void setLeggings(ItemStack leggings) {
        this.kitLeggings = leggings;
    }

    public void setBoots(ItemStack boots) {
        this.kitBoots = boots;
    }

    public void setOffhand(ItemStack offhand) {
        this.offhand = offhand;
    }

    public void setFill(ItemStack fill) {
        this.fill = fill;
    }

    /**
     * Gives the kit to the player. The kit always wins the slot it wants; anything
     * of the player's that it displaces becomes an overflow item, which is placed
     * in a free slot, then dropped or discarded depending on the configuration.
     */
    public void apply(Player player) {
        List<ItemStack> overflowItems = new ArrayList<>();
        boolean addOverflowItems = booleanOption("AddOverflowItemsOnKit", false);

        PlayerInventory inv = player.getInventory();

        if (kitHelmet != null) {
            collectOverflow(overflowItems, inv.getHelmet(), addOverflowItems);
            inv.setHelmet(kitHelmet);
        }

        if (kitChestplate != null) {
            collectOverflow(overflowItems, inv.getChestplate(), addOverflowItems);
            inv.setChestplate(kitChestplate);
        }

        if (kitLeggings != null) {
            collectOverflow(overflowItems, inv.getLeggings(), addOverflowItems);
            inv.setLeggings(kitLeggings);
        }

        if (kitBoots != null) {
            collectOverflow(overflowItems, inv.getBoots(), addOverflowItems);
            inv.setBoots(kitBoots);
        }

        Toolkit.setMaxHealth(player, maxHealth);

        for (int i = 0; i < 36; i++) {
            ItemStack kitItem = inventory.get(i);
            if (kitItem == null) {
                continue;
            }

            collectOverflow(overflowItems, inv.getItem(i), addOverflowItems);
            inv.setItem(i, kitItem);
        }

        if (offhand != null && Toolkit.versionToNumber() >= 19) {
            collectOverflow(overflowItems, inv.getItemInOffHand(), addOverflowItems);
            inv.setItemInOffHand(offhand);
        }

        if (addOverflowItems) {
            giveOverflowItems(player, overflowItems);
        }

        if (fill != null) {
            for (int i = 0; i < 36; i++) {
                if (Toolkit.isEmpty(inv.getItem(i))) {
                    inv.setItem(i, fill);
                }
            }
        }

        // last: make absolutely sure kit armor is equipped (and not left in inventory)
        forceEquipArmor(player);

        effects.forEach(player::addPotionEffect);
    }

    private void collectOverflow(List<ItemStack> overflowItems, ItemStack displaced, boolean addOverflowItems) {
        if (addOverflowItems && !Toolkit.isEmpty(displaced)) {
            overflowItems.add(displaced.clone());
        }
    }

    private void forceEquipArmor(Player player) {
        PlayerInventory inv = player.getInventory();

        ItemStack helmet = getHelmet();
        ItemStack chest = getChestplate();
        ItemStack legs = getLeggings();
        ItemStack boots = getBoots();

        if (!Toolkit.isEmpty(helmet)) {
            inv.setHelmet(helmet);
            removeFirstMatching(inv, helmet);
        }
        if (!Toolkit.isEmpty(chest)) {
            inv.setChestplate(chest);
            removeFirstMatching(inv, chest);
        }
        if (!Toolkit.isEmpty(legs)) {
            inv.setLeggings(legs);
            removeFirstMatching(inv, legs);
        }
        if (!Toolkit.isEmpty(boots)) {
            inv.setBoots(boots);
            removeFirstMatching(inv, boots);
        }
    }

    private void removeFirstMatching(PlayerInventory inv, ItemStack needle) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (Toolkit.isEmpty(s)) continue;

            if (s.isSimilar(needle)) {
                inv.setItem(i, new ItemStack(Material.AIR));
                return;
            }

            if (s.getType() == needle.getType()) {
                ItemMeta sm = s.getItemMeta();
                ItemMeta nm = needle.getItemMeta();
                if (sm != null && nm != null && sm.hasDisplayName() && nm.hasDisplayName()
                        && sm.getDisplayName().equals(nm.getDisplayName())) {
                    inv.setItem(i, new ItemStack(Material.AIR));
                    return;
                }
            }
        }
    }

    private void giveOverflowItems(Player p, List<ItemStack> overflowItems) {
        for (int i = 0; i < 36 && !overflowItems.isEmpty(); i++) {
            if (Toolkit.isEmpty(p.getInventory().getItem(i))) {
                p.getInventory().setItem(i, overflowItems.remove(0));
            }
        }

        if (overflowItems.isEmpty()) {
            return;
        }

        int remaining = overflowItems.size();

        if (booleanOption("DropRemainingOverflowItemsOnKit", false)) {
            for (ItemStack remainingOverflowItem : overflowItems) {
                p.getWorld().dropItem(p.getLocation(), remainingOverflowItem);
            }
            String message = stringOption("Message-OverflowItemsDropped", "");
            if (!message.isEmpty()) {
                p.sendMessage(message.replace("%number%", String.valueOf(remaining)));
            }
        } else {
            String message = stringOption("Message-OverflowItemsLost", "");
            if (!message.isEmpty()) {
                p.sendMessage(message.replace("%number%", String.valueOf(remaining)));
            }
        }
    }

    public void toResource(Resource resource) {
        resource.set("Kit.Permission", permission != null ? permission : "kp.kit." + name);
        resource.set("Kit.Cooldown", cooldown != null ? cooldown.formatted(true) : 0);
        resource.set("Kit.Level", level);
        resource.set("Kit.Health", maxHealth);
        if (minimumVersion != null) {
            resource.set("Kit.MinimumVersion", minimumVersion);
        }
        resource.save();

        AttributeWriter.itemStackToResource(resource, "Inventory.Armor.Helmet", kitHelmet);
        AttributeWriter.itemStackToResource(resource, "Inventory.Armor.Chestplate", kitChestplate);
        AttributeWriter.itemStackToResource(resource, "Inventory.Armor.Leggings", kitLeggings);
        AttributeWriter.itemStackToResource(resource, "Inventory.Armor.Boots", kitBoots);

        for (Integer slot : inventory.keySet()) {
            AttributeWriter.itemStackToResource(resource, "Inventory.Items." + slot, inventory.get(slot));
        }

        AttributeWriter.itemStackToResource(resource, "Inventory.Items.Offhand", offhand);
        AttributeWriter.itemStackToResource(resource, "Inventory.Items.Fill", fill);

        for (PotionEffect effect : effects) {
            AttributeWriter.potionEffectToResource(resource, "Effects", effect);
        }

        resource.save();
    }

    public String getName() { return name; }

    public String getMinimumVersion() { return minimumVersion; }

    public String getUnsupportedMaterial() { return unsupportedMaterial; }

    public String getPermission() { return permission; }

    public Cooldown getCooldown() { return cooldown; }

    public int getLevel() { return level; }

    public int getMaxHealth() { return maxHealth; }

    public Map<Integer, ItemStack> getInventory() { return inventory; }

    public List<PotionEffect> getEffects() { return effects; }

    public ItemStack getHelmet() { return kitHelmet; }

    public ItemStack getChestplate() { return kitChestplate; }

    public ItemStack getLeggings() { return kitLeggings; }

    public ItemStack getBoots() { return kitBoots; }

    public ItemStack getOffhand() { return offhand; }

    public ItemStack getFill() { return fill; }

}
