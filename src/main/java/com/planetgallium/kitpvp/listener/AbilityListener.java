package com.planetgallium.kitpvp.listener;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.api.Ability;
import com.planetgallium.kitpvp.util.Cooldown;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import com.planetgallium.kitpvp.api.PlayerAbilityEvent;
import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.util.Resources;
import com.planetgallium.kitpvp.util.Toolkit;

public class AbilityListener implements Listener {

	private final Arena arena;
	private final Resources resources;

	public AbilityListener(Game plugin) {
		this.arena = plugin.getArena();
		this.resources = plugin.getResources();
	}

	@EventHandler
	public void onAbility(PlayerAbilityEvent e) {
		Player p = e.getPlayer();

		if (!arena.getUtilities().isCombatActionPermittedInRegion(p)) {
			return;
		}

		Ability ability = e.getAbility();

		// an ability that arrived with the player's kit needs no separate permission
		if (!arena.getAbilities().hasAbilityPermission(p, ability.getName())) {
			p.sendMessage(resources.getMessages().fetchString("Messages.General.Permission")
					.replace("%permission%", arena.getAbilities().getPermissionNode(ability.getName())));
			return;
		}

		Cooldown cooldownRemaining = arena.getCooldowns().getRemainingCooldown(p, ability);
		if (cooldownRemaining.toSeconds() > 0) {
			p.sendMessage(resources.getMessages().fetchString("Messages.Error.CooldownAbility")
					.replace("%cooldown%", cooldownRemaining.formatted(false)));
			return;
		}

		if (ability.getMessage() != null)
			p.sendMessage(Toolkit.translate(ability.getMessage()));

		if (ability.getSound() != null)
			p.playSound(p.getLocation(), ability.getSound(),
					Math.max(0.0f, ability.getSoundVolume()),
					Math.max(0.5f, Math.min(2.0f, ability.getSoundPitch() <= 0 ? 1.0f : ability.getSoundPitch())));

		if (ability.getEffects().size() > 0)
			ability.getEffects().forEach(p::addPotionEffect);

		if (ability.getCommands().size() > 0)
			Toolkit.runCommands(p, ability.getCommands(), "none", "none");

		if (ability.getCooldown() == null) {
			// abilities without a cooldown are paid for with the item itself
			ItemStack abilityItem = Toolkit.getHandItemForInteraction(e.getOriginalInteractionEvent());
			Toolkit.consumeOneHandItem(e.getOriginalInteractionEvent(), abilityItem);
		} else {
			arena.getCooldowns().setAbilityCooldown(p.getName(), ability.getName());
		}
	}

}
