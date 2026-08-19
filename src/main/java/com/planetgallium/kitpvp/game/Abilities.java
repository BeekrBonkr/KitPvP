package com.planetgallium.kitpvp.game;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.api.Ability;
import com.planetgallium.kitpvp.api.Kit;
import com.planetgallium.kitpvp.util.Cooldown;
import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Resources;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class Abilities {

    private final Game plugin;
    private final Resources resources;
    private final Map<AbilityActivatorMetaData, Ability> activatorDataToAbility;

    /** Which abilities each kit hands out, worked out once per kit and reused on every click. */
    private final Map<String, Set<String>> kitToGrantedAbilities;

    public Abilities(Game plugin) {
        this.plugin = plugin;
        this.resources = plugin.getResources();
        this.activatorDataToAbility = new HashMap<>();
        this.kitToGrantedAbilities = new HashMap<>();

        rebuildCache();
    }

    public void rebuildCache() {
        activatorDataToAbility.clear();
        kitToGrantedAbilities.clear();

        for (Resource abilityResource : resources.getAbilityResources()) {
            Ability ability = getAbilityFromResource(abilityResource);
            AbilityActivatorMetaData activatorMetaData = new AbilityActivatorMetaData(ability.getActivator());
            activatorDataToAbility.put(activatorMetaData, ability);
        }
    }

    public Ability getAbilityByActivator(ItemStack potentialActivator) {
        if (Toolkit.isEmpty(potentialActivator)) {
            return null;
        }
        return activatorDataToAbility.get(new AbilityActivatorMetaData(potentialActivator));
    }

    /**
     * Whether the player may use this ability.
     *
     * An explicit permission node always decides — that is how an admin takes an
     * ability away from a kit that carries it. Where nothing has been set, an
     * ability that comes with the player's own kit is allowed: holding the kit is
     * the permission.
     */
    public boolean hasAbilityPermission(Player player, String abilityName) {
        String permissionNode = "kp.ability." + abilityName.toLowerCase();

        if (player.isPermissionSet(permissionNode)) {
            return player.hasPermission(permissionNode);
        }

        if (isGrantedByPlayersKit(player, abilityName)) {
            return true;
        }

        return player.hasPermission(permissionNode);
    }

    public String getPermissionNode(String abilityName) {
        return "kp.ability." + abilityName.toLowerCase();
    }

    private boolean isGrantedByPlayersKit(Player player, String abilityName) {
        Kit kit = plugin.getArena().getKits().getKitOfPlayer(player.getName());
        if (kit == null) {
            return false;
        }

        return getAbilitiesGrantedBy(kit).contains(abilityName.toLowerCase());
    }

    /**
     * The abilities a kit hands the player: the built-in ability that shares the
     * kit's name, plus every custom ability whose activator item the kit contains.
     */
    public Set<String> getAbilitiesGrantedBy(Kit kit) {
        Set<String> cached = kitToGrantedAbilities.get(kit.getName());
        if (cached != null) {
            return cached;
        }

        Set<String> granted = new HashSet<>();
        granted.add(kit.getName().toLowerCase());

        List<ItemStack> kitItems = new ArrayList<>(kit.getInventory().values());
        kitItems.add(kit.getHelmet());
        kitItems.add(kit.getChestplate());
        kitItems.add(kit.getLeggings());
        kitItems.add(kit.getBoots());
        kitItems.add(kit.getOffhand());

        for (ItemStack kitItem : kitItems) {
            Ability ability = getAbilityByActivator(kitItem);
            if (ability != null) {
                granted.add(ability.getName().toLowerCase());
            }
        }

        kitToGrantedAbilities.put(kit.getName(), granted);
        return granted;
    }

    private Ability getAbilityFromResource(Resource resource) {
        // the file name is the ability name; keeping the ".yml" on it made every
        // permission node read kp.ability.something.yml
        Ability ability = new Ability(Resources.trimYamlSuffix(resource.getName()));

        // Activator
        ItemStack activator = Toolkit.safeItemStack(resource.fetchString("Activator.Material"));
        ItemMeta activatorMeta = activator.getItemMeta();

        if (activatorMeta != null) {
            activatorMeta.setDisplayName(resource.fetchString("Activator.Name"));
            activator.setItemMeta(activatorMeta);
        }

        ability.setActivator(activator);

        // Cooldown
        if (resource.contains("Cooldown")) {
            String formattedCooldown = resource.fetchString("Cooldown.Cooldown");
            ability.setCooldown(new Cooldown(formattedCooldown));
        }

        // Message
        if (resource.contains("Message")) {
            ability.setMessage(resource.fetchString("Message.Message"));
        }

        // Sound
        if (resource.contains("Sound")) {
            Sound abilitySound = Toolkit.safeSound(resource.fetchString("Sound.Sound"));
            int abilitySoundPitch = resource.getInt("Sound.Pitch");
            int abilitySoundVolume = resource.getInt("Sound.Volume");

            ability.setSound(abilitySound, abilitySoundPitch, abilitySoundVolume);
        }

        // Effects
        if (resource.contains("Effects")) {
            ConfigurationSection effectSection = resource.getConfigurationSection("Effects");

            if (effectSection != null) {
                for (String effectName : effectSection.getKeys(false)) {
                    PotionEffectType effectType = Toolkit.safePotionEffectType(effectName);
                    int amplifier = resource.getInt("Effects." + effectName + ".Amplifier");
                    int duration = resource.getInt("Effects." + effectName + ".Duration");

                    ability.addEffect(effectType, amplifier, duration);
                }
            }
        }

        // Commands
        if (resource.contains("Commands")) {
            for (String command : resource.getStringList("Commands")) {
                ability.addCommand(command);
            }
        }

        return ability;
    }

    public static class AbilityActivatorMetaData {

        private final String materialName;
        private final String displayName;

        public AbilityActivatorMetaData(ItemStack buildFromItem) {
            this.materialName = buildFromItem.getType().toString();

            String foundDisplayName = null;
            if (buildFromItem.hasItemMeta()) {
                ItemMeta itemMeta = buildFromItem.getItemMeta();
                if (itemMeta != null && itemMeta.hasDisplayName()) {
                    foundDisplayName = itemMeta.getDisplayName();
                }
            }
            this.displayName = foundDisplayName;
        }

        @Override
        public boolean equals(Object otherObject) {
            if (otherObject instanceof AbilityActivatorMetaData) {
                AbilityActivatorMetaData otherAbilityData = (AbilityActivatorMetaData) otherObject;
                return Objects.equals(this.displayName, otherAbilityData.getDisplayName()) &&
                        Objects.equals(this.materialName, otherAbilityData.getMaterialName());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(displayName, materialName);
        }

        public String getDisplayName() { return displayName; }

        public String getMaterialName() { return materialName; }

    }

}
