package com.planetgallium.kitpvp.item;

import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XPotion;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.potion.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class AttributeParser {

    // PotionEffect
    private static final int FALLBACK_EFFECT_DURATION_SECONDS = 10;
    private static final int FALLBACK_EFFECT_AMPLIFIER_NON_ZERO_BASED = 1;

    // ItemStack
    private static final Material FALLBACK_ITEM_MATERIAL = Material.BEDROCK;
    private static final int FALLBACK_ITEM_AMOUNT = 1;
    private static final Enchantment FALLBACK_ITEM_ENCHANTMENT = Enchantment.THORNS;
    private static final int FALLBACK_ITEM_ENCHANTMENT_AMPLIFIER = 1;

    /**
     * Potion types that have a level II variant, by name rather than by constant:
     * the PotionType constants were renamed in 1.20.5, so naming them directly
     * stops the class loading on newer servers.
     */
    private static final Set<String> UPGRADEABLE_POTION_NAMES = new HashSet<>(Arrays.asList(
            "INSTANT_DAMAGE", "HARMING", "INSTANT_HEAL", "HEALING", "JUMP", "LEAPING",
            "POISON", "REGEN", "REGENERATION", "SPEED", "SWIFTNESS", "STRENGTH"));

    public static List<PotionEffect> getEffectsFromPath(Resource resource, String path) {

        List<PotionEffect> effects = new ArrayList<>();
        ConfigurationSection effectsSection = resource.getConfigurationSection(path);

        if (effectsSection != null) {

            for (String effectName : effectsSection.getKeys(false)) {
                // an unknown effect name used to throw out of kit loading entirely
                PotionEffectType type = Toolkit.safePotionEffectType(effectName);
                int amplifier = resource.getInt(path + "." + effectName + ".Amplifier");
                int duration = resource.getInt(path + "." + effectName + ".Duration");

                effects.add(new PotionEffect(type,
                        duration != 0 ? duration : FALLBACK_EFFECT_DURATION_SECONDS,
                        amplifier != 0 ? amplifier : FALLBACK_EFFECT_AMPLIFIER_NON_ZERO_BASED));
            }

        }

        return effects;

    }

    public static ItemStack getItemStackFromPath(Resource resource, String path) throws IllegalAccessException,
            NoSuchMethodException, InvocationTargetException {
        if (!resource.contains(path)) return null;

        ItemStack item = XMaterial.matchXMaterial(FALLBACK_ITEM_MATERIAL).parseItem();
        ItemMeta meta = item.getItemMeta();

        //          BASIC ITEM INFORMATION          //

        if (meta != null) {
            if (resource.contains(path + ".Name")) {
                meta.setDisplayName(resource.fetchString(path + ".Name"));
            }

            if (resource.contains(path + ".Lore")) {
                meta.setLore(resource.getStringList(path + ".Lore"));
            }
        }

        if (resource.contains(path + ".Material")) {
            String materialName = resource.fetchString(path + ".Material");
            Material material = Toolkit.safeMaterial(materialName);

            item.setType(material);
        }

        if (resource.contains(path + ".Amount")) {
            int amount = resource.getInt(path + ".Amount");
            item.setAmount(Math.max(1, Math.min(amount, item.getType().getMaxStackSize())));
        } else {
            item.setAmount(FALLBACK_ITEM_AMOUNT);
        }

        if (meta != null) {
            item.setItemMeta(meta);
        }

        //              CUSTOM ATTRIBUTES            //

        if (resource.contains(path + ".Effects")) {
            item = setEffectsFromPath(item, resource, path);
        }

        if (Game.getInstance().getResources().getConfig().getBoolean("Arena.PreventItemDurabilityDamage")) {
            setUnbreakable(item);
        }

        if (resource.contains(path + ".Dye")) {
            dyeItem(item, Toolkit.getColorFromConfig(resource, path + ".Dye"));
        }

        if (resource.contains(path + ".Skull")) {
            setSkull(item, resource.fetchString(path + ".Skull"));
        }

        if (resource.contains(path + ".Durability")) {
            setCustomDurability(item, resource.getInt(path + ".Durability"));
        }

        if (resource.contains(path + ".Enchantments")) {
            Map<Enchantment, Integer> enchantments = new HashMap<>();
            ConfigurationSection section = resource.getConfigurationSection(path + ".Enchantments");

            if (section != null) {
                for (String enchantmentName : section.getKeys(false)) {
                    Enchantment enchantment = FALLBACK_ITEM_ENCHANTMENT;
                    Optional<XEnchantment> enchantmentFromConfig =
                            XEnchantment.matchXEnchantment(enchantmentName.toUpperCase());
                    if (enchantmentFromConfig.isPresent()) {
                        enchantment = enchantmentFromConfig.get().getEnchant();
                    } else {
                        Toolkit.warnOnce("enchantment:" + enchantmentName,
                                String.format("&7[&b&lKIT-PVP&7] &cUnknown enchantment [%s], defaulting to [THORNS].",
                                        enchantmentName));
                    }
                    int amplifier = resource.getInt(path + ".Enchantments." + enchantmentName);

                    enchantments.put(enchantment != null ? enchantment : FALLBACK_ITEM_ENCHANTMENT,
                            amplifier != 0 ? amplifier : FALLBACK_ITEM_ENCHANTMENT_AMPLIFIER);
                }
            }

            item.addUnsafeEnchantments(enchantments);
        }

        return item;
    }

    private static void setCustomDurability(ItemStack item, int damageAmount) {
        if (Toolkit.versionToNumber() < 113) {
            item.setDurability((short) damageAmount);

        } else {
            ItemMeta meta = item.getItemMeta();

            if (meta instanceof Damageable) {
                ((Damageable) meta).setDamage(damageAmount);
                item.setItemMeta(meta);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void setSkull(ItemStack item, String skullOwner) {
        item.setType(XMaterial.PLAYER_HEAD.parseMaterial());

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta) {
            ((SkullMeta) meta).setOwner(skullOwner);
            item.setItemMeta(meta);
        }
    }

    private static void dyeItem(ItemStack item, Color color) {
        ItemMeta meta = item.getItemMeta();

        if (meta instanceof LeatherArmorMeta) {
            ((LeatherArmorMeta) meta).setColor(color);
            item.setItemMeta(meta);
        }
    }

    private static void setUnbreakable(ItemStack item) throws InvocationTargetException, IllegalAccessException,
            NoSuchMethodException {
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return;
        }

        if (Toolkit.versionToNumber() <= 114) {
            Method spigotMethod = meta.getClass().getMethod("spigot");
            spigotMethod.setAccessible(true);

            Object spigotInstance = spigotMethod.invoke(meta);
            Class<?> spigotClass = spigotInstance.getClass();

            Method setUnbreakableMethod = spigotClass.getDeclaredMethod("setUnbreakable", boolean.class);
            setUnbreakableMethod.setAccessible(true);

            setUnbreakableMethod.invoke(spigotInstance, true);
        } else {
            meta.setUnbreakable(true);
        }

        // getItemMeta hands back a copy, so the change only counts once it is put back
        item.setItemMeta(meta);
    }

    private static ItemStack setEffectsFromPath(ItemStack item, Resource resource, String path) {
        String effectsPath = path + ".Effects";
        ConfigurationSection effectsSection = resource.getConfigurationSection(effectsPath);

        if (effectsSection == null || effectsSection.getKeys(false).isEmpty()) {
            return item;
        }

        String firstChildEffectName = effectsSection.getKeys(false).iterator().next();
        String firstChildPath = effectsPath + "." + firstChildEffectName;
        boolean isCustom = resource.contains(firstChildPath + ".Duration");

        if (isCustom) {
            setCustomEffectsFromPath(item, resource, effectsPath);
            return item;
        }

        return setVanillaEffectsFromPath(item, resource, effectsPath);
    }

    @SuppressWarnings("deprecation")
    private static ItemStack setVanillaEffectsFromPath(ItemStack item, Resource resource, String path) {
        ConfigurationSection section = resource.getConfigurationSection(path);
        if (section == null || section.getKeys(false).isEmpty()) {
            return item;
        }

        String firstChildEffectName = section.getKeys(false).iterator().next();
        String firstChildPath = path + "." + firstChildEffectName;

        Optional<XPotion> matchedPotion = XPotion.matchXPotion(firstChildEffectName);
        PotionType potionType = matchedPotion.map(XPotion::getPotionType).orElse(null);

        if (potionType == null) {
            Toolkit.warnOnce("potion-effect:" + firstChildEffectName,
                    "&7[&b&lKIT-PVP&7] &cThere is no potion called [" + firstChildEffectName +
                    "] on this server version; the item was left as a plain potion.");
            return item;
        }

        boolean isUpgraded = resource.getBoolean(firstChildPath + ".Upgraded");
        boolean isExtended = resource.getBoolean(firstChildPath + ".Extended");

        if (Toolkit.versionToNumber() == 18) {
            boolean isSplash = "SPLASH_POTION".equals(
                    resource.fetchStringOrNull(path.replace("Effects", "") + ".Type"));
            Potion potion = Potion.fromItemStack(item);
            potion.setSplash(isSplash);
            potion.setType(potionType);
            potion.setLevel(getVanillaLevel(potionType, isUpgraded));
            if (isExtended) potion.setHasExtendedDuration(true);

            return potion.toItemStack(item.getAmount());
        }

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof PotionMeta) {
            Toolkit.applyBasePotionType((PotionMeta) meta, potionType, isExtended, isUpgraded);
            item.setItemMeta(meta);
        }

        return item;
    }

    private static void setCustomEffectsFromPath(ItemStack item, Resource resource, String path) {
        ItemMeta meta = item.getItemMeta();

        if (!(meta instanceof PotionMeta)) {
            return;
        }

        PotionMeta potionMeta = (PotionMeta) meta;
        ConfigurationSection section = resource.getConfigurationSection(path);

        if (section == null) {
            return;
        }

        for (String effectName : section.getKeys(false)) {
            String specificPath = path + "." + effectName;
            potionMeta.addCustomEffect(new PotionEffect(Toolkit.safePotionEffectType(effectName),
                    resource.getInt(specificPath + ".Duration") * 20,
                    Math.max(0, resource.getInt(specificPath + ".Amplifier") - 1)), true);
        }

        item.setItemMeta(potionMeta);
    }

    private static int getVanillaLevel(PotionType type, boolean isUpgraded) {
        return isUpgraded && UPGRADEABLE_POTION_NAMES.contains(type.name()) ? 2 : 1;
    }

}
