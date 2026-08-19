package com.planetgallium.kitpvp.listener;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XPotion;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.api.Kit;
import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.game.Utilities;
import com.planetgallium.kitpvp.util.CacheManager;
import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Resources;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ItemListener implements Listener {

	/**
	 * The Witch's potion pool, by name rather than by PotionType constant: the
	 * constants were renamed in 1.20.5 (INSTANT_HEAL became HEALING and so on),
	 * which turned the old list into a NoSuchFieldError on newer servers.
	 * Weights match the original roll: 10/10/20/20/20/20 out of 100.
	 */
	private static final String[] WITCH_POTION_NAMES =
			{"INSTANT_DAMAGE", "INSTANT_HEAL", "POISON", "REGENERATION", "SPEED", "SLOWNESS"};
	private static final int[] WITCH_POTION_WEIGHTS = {10, 10, 20, 20, 20, 20};

	private final Game plugin;
	private final Arena arena;
	private final Utilities utilities;
	private final Resources resources;
	private final Resource config;
	private final Resource abilities;
	private final Random random = new Random();

	private List<PotionType> witchPotionPool;

	public ItemListener(Game plugin) {
		this.plugin = plugin;
		this.arena = plugin.getArena();
		this.utilities = plugin.getArena().getUtilities();
		this.resources = plugin.getResources();
		this.config = resources.getConfig();
		this.abilities = resources.getAbilities();
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		Player p = e.getPlayer();

		if (Toolkit.inArena(p) &&
				(e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {

			ItemStack interactedItem = Toolkit.getHandItemForInteraction(e);
			ItemMeta interactedItemMeta = interactedItem.getItemMeta();

			if (isAbility(p, interactedItem, "Kangaroo", "SADDLE")) {
				kangarooAbility(e, p, interactedItem);

			} else if (isAbility(p, interactedItem, "Soldier", "IRON_HOE")) {
				soldierAbility(e, p, interactedItem);

			} else if (isAbility(p, interactedItem, "Warper", "ENDER_EYE")) {
				warperAbility(e, p, interactedItem);

			} else if (isAbility(p, interactedItem, "Ninja", "NETHER_STAR")) {
				ninjaAbility(e, p, interactedItem);

			} else if (isAbility(p, interactedItem, "Bomber", "COAL")) {
				bomberAbility(e, p, interactedItem);

			} else if (isAbility(p, interactedItem, "Witch", "GLASS_BOTTLE")) {
				e.setCancelled(true);
				CacheManager.getPotionSwitcherUsers().add(p.getName());
				Toolkit.setHandItemForInteraction(e, createWitchPotion());

			} else if (isAbility(p, interactedItem, "Thunderbolt", "BLAZE_ROD") ||
					isAbility(p, interactedItem, "Vampire", "GHAST_TEAR")) {
				// these two only do anything when used on somebody
				p.sendMessage(resources.getMessages().fetchString("Messages.Error.Player"));

			} else if (isSplashPotion(interactedItem)) {
				witchAbility(e, p, interactedItem, interactedItemMeta);

			} else if ((Toolkit.hasMatchingMaterial(interactedItem, "SLIME_BALL") ||
					Toolkit.hasMatchingMaterial(interactedItem, "MAGMA_CREAM"))
						&& interactedItem.hasItemMeta()) {
				archerAbility(e, p, interactedItem, interactedItemMeta);

			} else if (Toolkit.hasMatchingMaterial(interactedItem, "TNT")) {
				specialTNT(e, p, interactedItem);

			}

			/* Kit Item and custom Arena Items */

			if (config.contains("Items.Kits") &&
					Toolkit.hasMatchingMaterial(interactedItem, config.fetchString("Items.Kits.Material"))) {

				if (Toolkit.hasMatchingDisplayName(interactedItem, config.fetchString("Items.Kits.Name"))) {
					Toolkit.runCommands(p, config.getStringList("Items.Kits.Commands"),
							"none", "none");

					if (config.getBoolean("Items.Kits.Menu")) {
						arena.getMenus().getKitMenu().open(p);
					}

					e.setCancelled(true);
				}

			} else if (interactedItem.hasItemMeta()) {
				ConfigurationSection items = config.getConfigurationSection("Items");

				if (items == null) {
					return;
				}

				for (String identifier : items.getKeys(false)) {
					String itemPath = "Items." + identifier;

					// a disabled item is skipped, not a reason to stop looking at the rest
					if (!config.getBoolean(itemPath + ".Enabled")) {
						continue;
					}

					String itemMaterialName = config.fetchString(itemPath + ".Material");
					if (Toolkit.hasMatchingMaterial(interactedItem, itemMaterialName)) {
						if (Toolkit.hasMatchingDisplayName(interactedItem, config.fetchString(itemPath + ".Name"))) {
							Toolkit.runCommands(p, config.getStringList(itemPath + ".Commands"),
									"none", "none");
							e.setCancelled(true);
						}
					}
				}
			}

		}
	}

	public boolean isAbility(Player p, ItemStack interactedItem, String kitName, String abilityMaterialName) {
		return interactedItem.getType() == Toolkit.safeMaterial(abilityMaterialName) &&
				isBuiltInAbilityItem(p, kitName, interactedItem);
	}

	@EventHandler
	public void onInteract(PlayerInteractEntityEvent e) {
		Player damager = e.getPlayer();

		if (Toolkit.inArena(damager) && e.getRightClicked().getType() == EntityType.PLAYER) {
			ItemStack interactedItem = Toolkit.getHandItemForInteraction(e);
			Player damagedPlayer = (Player) e.getRightClicked();

			if (isAbility(damager, interactedItem, "Thunderbolt", "BLAZE_ROD")) {
				thunderboltAbility(e, damager, damagedPlayer, interactedItem);

			} else if (isAbility(damager, interactedItem, "Vampire", "GHAST_TEAR")) {
				vampireAbliity(e, damager, damagedPlayer, interactedItem);
			}
		}
	}

	private boolean isBuiltInAbilityItem(Player p, String kitName, ItemStack interactedItem) {
		if (!Toolkit.hasMatchingDisplayName(interactedItem,
				abilities.fetchString("Abilities." + kitName + ".Item.Name"))) {
			return false;
		}

		// holding the kit is permission enough for the ability that comes with it,
		// unless an admin has explicitly set the node either way
		if (!arena.getAbilities().hasAbilityPermission(p, kitName)) {
			p.sendMessage(resources.getMessages().fetchString("Messages.General.Permission")
					.replace("%permission%", arena.getAbilities().getPermissionNode(kitName)));
			return false;
		}

		if (config.getBoolean("Arena.AbilitiesRequireKit") && !arena.getKits().playerHasKit(p.getName())) {
			p.sendMessage(resources.getMessages().fetchString("Messages.Error.Kit"));
			return false;
		}

		return utilities.isCombatActionPermittedInRegion(p);
	}

	private void useBuiltInAbilityItem(Event e, Player p, Player clicked, ItemStack abilityItem,
									   String kitName) {
		String abilityPrefix = "Abilities." + kitName;

		if (abilities.getBoolean(abilityPrefix + ".Message.Enabled")) {
			String abilityMessage = abilities.fetchString(abilityPrefix + ".Message.Message");
			if (clicked != null) abilityMessage = abilityMessage.replace("%player%", clicked.getName());
			p.sendMessage(abilityMessage);
		}

		Toolkit.consumeOneHandItem(e, abilityItem);

		if (abilities.getBoolean(abilityPrefix + ".Sound.Enabled")) {
			Toolkit.playSoundToPlayer(p, abilities.fetchString(abilityPrefix + ".Sound.Sound"),
					abilities.getInt(abilityPrefix + ".Sound.Pitch"));
		}
	}

	private void kangarooAbility(PlayerInteractEvent e, Player p, ItemStack abilityItem) {
		p.setVelocity(new Vector(0, 2, 0));
		p.setFallDistance(config.getBoolean("Arena.PreventFallDamage") ? -1000000 : -30);

		useBuiltInAbilityItem(e, p, p, abilityItem, "Kangaroo");
	}

	private void soldierAbility(PlayerInteractEvent e, Player p, ItemStack abilityItem) {
		Snowball ammo = p.launchProjectile(Snowball.class);
		ammo.setCustomName("bullet");
		ammo.setVelocity(p.getLocation().getDirection().multiply(2.5));

		useBuiltInAbilityItem(e, p, p, abilityItem, "Soldier");
	}

	private void specialTNT(PlayerInteractEvent e, Player p, ItemStack abilityItem) {
		if (config.getBoolean("TNT.Enabled") &&
				Toolkit.hasMatchingDisplayName(abilityItem, config.fetchString("TNT.Name"))) {

			if (!utilities.isCombatActionPermittedInRegion(p)) {
				return;
			}

			Location handLocation = p.getLocation();
			handLocation.setY(handLocation.getY() + 1.0);
			Vector direction = handLocation.getDirection();

			Entity entity = p.getWorld().spawn(handLocation, TNTPrimed.class);
			entity.setVelocity(direction.multiply(1.5));
			entity.setCustomName(p.getName());

			e.setCancelled(true);

			Toolkit.consumeOneHandItem(e, abilityItem);
		}
	}

	private void warperAbility(PlayerInteractEvent e, Player p, ItemStack abilityItem) {
		e.setCancelled(true);

		Toolkit.Nearest nearest = Toolkit.getNearestPlayer(p, config.getInt("PlayerTracker.TrackBelowY"));

		if (nearest == null || !arena.getKits().playerHasKit(nearest.getPlayer().getName())) {
			p.sendMessage(resources.getMessages().fetchString("Messages.Other.Players"));
			return;
		}

		Player target = nearest.getPlayer();

		p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 5));
		p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 5));

		useBuiltInAbilityItem(e, p, p, abilityItem, "Warper");

		new BukkitRunnable() {
			@Override
			public void run() {
				// the target may have moved or left in the meantime, so their position
				// is read at the moment of the warp rather than when it was started
				if (p.isOnline() && target.isOnline() && target.getWorld().equals(p.getWorld())) {
					p.teleport(target.getLocation());
				}
			}
		}.runTaskLater(plugin, 5L);
	}

	private void ninjaAbility(PlayerInteractEvent e, Player p, ItemStack abilityItem) {
		Kit kitWhenUsed = arena.getKits().getKitOfPlayer(p.getName());

		ItemStack previousHelmet = p.getInventory().getHelmet();
		ItemStack previousChestplate = p.getInventory().getChestplate();
		ItemStack previousLeggings = p.getInventory().getLeggings();
		ItemStack previousBoots = p.getInventory().getBoots();

		p.getInventory().setArmorContents(null);

		for (Entity entity : p.getNearbyEntities(3, 3, 3)) {
			if (entity instanceof Player) {
				Player nearby = (Player) entity;
				nearby.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
				nearby.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 0));
			}
		}

		p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2));
		p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0));
		p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 100, 0));

		useBuiltInAbilityItem(e, p, p, abilityItem, "Ninja");

		new BukkitRunnable() {
			@Override
			public void run() {
				// only give the armour back if this is still the same kit it was taken
				// from; otherwise a kit swap in the meantime would be handed Ninja's gear
				Kit currentKit = arena.getKits().getKitOfPlayer(p.getName());

				if (p.isOnline() && currentKit != null && currentKit == kitWhenUsed) {
					p.getInventory().setHelmet(previousHelmet);
					p.getInventory().setChestplate(previousChestplate);
					p.getInventory().setLeggings(previousLeggings);
					p.getInventory().setBoots(previousBoots);
				}
			}
		}.runTaskLater(plugin, 100L);
	}

	private void bomberAbility(PlayerInteractEvent e, Player p, ItemStack abilityItem) {
		p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 5 * 20, 2));

		new BukkitRunnable() {
			public int t = 5;

			@Override
			public void run() {
				// the run of TNT stops if the player leaves, dies, drops their kit or
				// walks somewhere that combat is not allowed
				if (t <= 0 || !p.isOnline() || p.getGameMode() == GameMode.SPECTATOR ||
						!arena.getKits().playerHasKit(p.getName()) || !Toolkit.inArena(p) ||
						!utilities.isCombatActionPermittedInRegion(p, false)) {
					cancel();
					return;
				}

				Entity entity = p.getWorld().spawn(p.getLocation(), TNTPrimed.class);
				entity.setCustomName(p.getName());

				Toolkit.playSoundToPlayer(p, abilities.fetchString("Abilities.Bomber.Sound.Sound"),
						abilities.getInt("Abilities.Bomber.Sound.Pitch"));
				t--;
			}
		}.runTaskTimer(plugin, 0L, 20L);

		useBuiltInAbilityItem(e, p, null, abilityItem, "Bomber");
	}

	private void archerAbility(PlayerInteractEvent e, Player p, ItemStack abilityItem, ItemMeta abilityItemMeta) {
		if (abilityItemMeta == null) {
			return;
		}

		boolean shouldToggleOn = Toolkit.hasMatchingMaterial(abilityItem, "SLIME_BALL");
		String firePath = shouldToggleOn ? "Fire" : "NoFire";
		String fireItem = shouldToggleOn ? "MAGMA_CREAM" : "SLIME_BALL";

		if (Toolkit.hasMatchingDisplayName(abilityItem, abilities.fetchString("Abilities.Archer.Item.Fire")) ||
				Toolkit.hasMatchingDisplayName(abilityItem, abilities.fetchString("Abilities.Archer.Item.NoFire"))) {

			if (!arena.getAbilities().hasAbilityPermission(p, "Archer")) {
				return;
			}

			abilityItem.setType(Toolkit.safeMaterial(fireItem));
			abilityItemMeta.setDisplayName(abilities.fetchString("Abilities.Archer.Item." + firePath));
			abilityItem.setItemMeta(abilityItemMeta);

			Toolkit.setHandItemForInteraction(e, abilityItem);

			if (abilities.getBoolean("Abilities.Archer.Message.Enabled")) {
				p.sendMessage(abilities.fetchString("Abilities.Archer.Message." + firePath));
			}

			Toolkit.playSoundToPlayer(p, "UI_BUTTON_CLICK", 1);
		}
	}

	private void witchAbility(PlayerInteractEvent e, Player p, ItemStack potionItem, ItemMeta abilityItemMeta) {
		if (potionItem.hasItemMeta() && abilityItemMeta != null && abilityItemMeta.hasLore() &&
				Toolkit.singleLineLoreMatches(potionItem, "X")) {

			Toolkit.SlotWrapper slotUsed = Toolkit.getSlotUsedForInteraction(e);
			int heldSlot = p.getInventory().getHeldItemSlot();
			ItemStack randomizedWitchPotion = createWitchPotion();

			new BukkitRunnable() {
				@Override
				public void run() {
					Kit playerKit = arena.getKits().getKitOfPlayer(p.getName());

					if (!p.isOnline() || playerKit == null ||
							!CacheManager.getPotionSwitcherUsers().contains(p.getName())) {
						return;
					}

					// only refill a slot that is still free, so whatever the player put
					// there in the last five seconds is not quietly replaced
					ItemStack current = p.getInventory().getItem(heldSlot);
					if (Toolkit.isEmpty(current) || Toolkit.singleLineLoreMatches(current, "X")) {
						slotUsed.placeItemInSlot(p, randomizedWitchPotion);
					} else if (Toolkit.countEmptyMainSlots(p) > 0) {
						p.getInventory().addItem(randomizedWitchPotion);
					} else {
						return;
					}

					if (abilities.getBoolean("Abilities.Witch.Message.Enabled")) {
						p.sendMessage(abilities.fetchString("Abilities.Witch.Message.Message"));
					}

					if (abilities.getBoolean("Abilities.Witch.Sound.Enabled")) {
						Toolkit.playSoundToPlayer(p, abilities.fetchString("Abilities.Witch.Sound.Sound"),
								abilities.getInt("Abilities.Witch.Sound.Pitch"));
					}
				}
			}.runTaskLater(plugin, 5 * 20L);
		}
	}

	private void thunderboltAbility(PlayerInteractEntityEvent e, Player damager, Player damagedPlayer,
									ItemStack abilityItem) {
		damager.getWorld().strikeLightningEffect(e.getRightClicked().getLocation());

		// damage with an attacker attached, so the server credits the kill, the
		// death message names them, and the streak counts it
		damagedPlayer.damage(4.0, damager);
		damagedPlayer.setFireTicks(5 * 20);

		useBuiltInAbilityItem(e, damager, damagedPlayer, abilityItem, "Thunderbolt");
	}

	private void vampireAbliity(PlayerInteractEntityEvent e, Player damager, Player damagedPlayer,
								ItemStack abilityItem) {
		damagedPlayer.damage(4.0, damager);
		Toolkit.playSoundToPlayer(damagedPlayer, "ENTITY_GENERIC_DRINK", 1);

		double maxHealth = Toolkit.getMaxHealth(damager);
		if (damager.getHealth() < maxHealth) {
			damager.setHealth(Math.min(damager.getHealth() + 4.0, maxHealth));
			damager.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 2, 1));
		}

		useBuiltInAbilityItem(e, damager, damagedPlayer, abilityItem, "Vampire");
	}

	@EventHandler
	public void onProjectileHitsEntity(EntityDamageByEntityEvent e) {
		if (e.getEntity() instanceof Player && e.getCause() == DamageCause.PROJECTILE) {

			if (e.getDamager() instanceof Snowball) {
				Player damagedPlayer = (Player) e.getEntity();
				Snowball snowball = (Snowball) e.getDamager();
				hitBySnowball(damagedPlayer, snowball);

			} else if (e.getDamager() instanceof Egg) {
				Player damagedPlayer = (Player) e.getEntity();
				Egg egg = (Egg) e.getDamager();
				hitByEgg(damagedPlayer, egg);
			}
		}
	}

	private void hitBySnowball(Player damagedPlayer, Snowball snowball) {
		if (snowball.getCustomName() != null && snowball.getCustomName().equals("bullet")) {
			if (Toolkit.inArena(damagedPlayer) && arena.getKits().playerHasKit(damagedPlayer.getName())) {
				Player shooter = snowball.getShooter() instanceof Player ? (Player) snowball.getShooter() : null;

				if (shooter != null) {
					damagedPlayer.damage(4.5, shooter);
				} else {
					damagedPlayer.damage(4.5);
				}
			}
		}
	}

	private void hitByEgg(Player damagedPlayer, Egg egg) {
		if (!isTricksterEgg(egg)) {
			return;
		}

		if (Toolkit.inArena(damagedPlayer) && arena.getKits().playerHasKit(damagedPlayer.getName())) {

			if (egg.getShooter() instanceof Player) {
				Player shooter = (Player) egg.getShooter();
				Location shooterLocation = shooter.getLocation();

				if (!utilities.isCombatActionPermittedInRegion(damagedPlayer, false)) {
					shooter.sendMessage(resources.getMessages().fetchString("Messages.Error.PVP"));
					return;
				}

				shooter.teleport(damagedPlayer);
				damagedPlayer.teleport(shooterLocation);

				if (abilities.getBoolean("Abilities.Trickster.Message.Enabled")) {
					shooter.sendMessage(abilities.fetchString("Abilities.Trickster.Message.Message")
							.replace("%player%", damagedPlayer.getName()));
				}

				if (abilities.getBoolean("Abilities.Trickster.Sound.Enabled")) {
					Toolkit.playSoundToPlayer(shooter,
							abilities.fetchString("Abilities.Trickster.Sound.Sound"),
							abilities.getInt("Abilities.Trickster.Sound.Pitch"));
					Toolkit.playSoundToPlayer(damagedPlayer,
							abilities.fetchString("Abilities.Trickster.Sound.Sound"),
							abilities.getInt("Abilities.Trickster.Sound.Pitch"));
				}
			}
		}
	}

	/** Egg#getItem does not exist before 1.15, so the check degrades instead of throwing. */
	private boolean isTricksterEgg(Egg egg) {
		try {
			return Toolkit.hasMatchingDisplayName(egg.getItem(),
					abilities.fetchString("Abilities.Trickster.Item.Name"));
		} catch (Throwable ignored) {
			return egg.getShooter() instanceof Player &&
					arena.getKits().playerHasKit(((Player) egg.getShooter()).getName());
		}
	}

	@EventHandler
	public void onBowShot(EntityShootBowEvent e) {
		if (e.getEntity() instanceof Player && Toolkit.inArena(e.getEntity())) {
			Player p = (Player) e.getEntity();

			if (!arena.getAbilities().hasAbilityPermission(p, "Archer")) {
				return;
			}

			int ammoSlot = getItemByMeta(Toolkit.safeMaterial("MAGMA_CREAM"),
					abilities.fetchString("Abilities.Archer.Item.Fire"), p);

			if (ammoSlot != -1) {
				ItemStack ammo = p.getInventory().getItem(ammoSlot);

				e.getProjectile().setFireTicks(1000);
				Toolkit.playSoundToPlayer(p, abilities.fetchString("Abilities.Archer.Sound.Sound"),
						abilities.getInt("Abilities.Archer.Sound.Pitch"));

				if (ammo.getAmount() <= 1) {
					p.getInventory().setItem(ammoSlot, new ItemStack(Material.AIR));
				} else {
					ammo.setAmount(ammo.getAmount() - 1);
				}
			}
		}
	}

	private int getItemByMeta(Material type, String displayName, Player p) {
		// the main inventory only: reading past its end used to depend on the
		// server version being guessed correctly
		for (int i = 0; i < 36; i++) {
			ItemStack item = p.getInventory().getItem(i);
			if (item != null && item.getType() == type && Toolkit.hasMatchingDisplayName(item, displayName)) {
				return i;
			}
		}

		if (Toolkit.versionToNumber() >= 19) {
			ItemStack offhand = p.getInventory().getItemInOffHand();
			if (offhand.getType() == type && Toolkit.hasMatchingDisplayName(offhand, displayName)) {
				return 40;
			}
		}
		return -1;
	}

	private ItemStack createWitchPotion() {
		PotionType randomPotionType = randomPotionType();

		ItemStack potionStack = Toolkit.safeItemStack(
				Toolkit.versionToNumber() == 18 ? "POTION" : "SPLASH_POTION");

		if (Toolkit.versionToNumber() == 18) {
			// the legacy Potion class is the only way to make a splash potion on 1.8
			Potion potion = new Potion(randomPotionType, 1);
			potion.setSplash(true);

			potionStack = potion.toItemStack(1);
			Toolkit.appendToLore(potionStack, "X");
			return potionStack;
		}

		ItemMeta meta = potionStack.getItemMeta();
		if (meta instanceof PotionMeta && randomPotionType != null) {
			Toolkit.applyBasePotionType((PotionMeta) meta, randomPotionType);
			potionStack.setItemMeta(meta);
		}

		Toolkit.appendToLore(potionStack, "X");
		return potionStack;
	}

	private PotionType randomPotionType() {
		if (witchPotionPool == null) {
			witchPotionPool = new ArrayList<>();

			for (int i = 0; i < WITCH_POTION_NAMES.length; i++) {
				PotionType resolved = XPotion.matchXPotion(WITCH_POTION_NAMES[i])
						.map(XPotion::getPotionType).orElse(null);

				if (resolved == null) {
					Toolkit.warnOnce("witch-potion:" + WITCH_POTION_NAMES[i],
							"&7[&b&lKIT-PVP&7] &eThe Witch potion " + WITCH_POTION_NAMES[i] +
							" does not exist on this server version and was left out of the pool.");
					continue;
				}

				for (int weight = 0; weight < WITCH_POTION_WEIGHTS[i]; weight++) {
					witchPotionPool.add(resolved);
				}
			}
		}

		if (witchPotionPool.isEmpty()) {
			return null;
		}
		return witchPotionPool.get(random.nextInt(witchPotionPool.size()));
	}

	public boolean isSplashPotion(ItemStack item) {
		return Toolkit.hasMatchingMaterial(item, Toolkit.versionToNumber() == 18 ? "POTION" : "SPLASH_POTION");
	}

}
