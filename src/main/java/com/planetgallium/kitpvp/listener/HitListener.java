package com.planetgallium.kitpvp.listener;

import com.cryptomorin.xseries.XSound;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.util.Resource;
import com.planetgallium.kitpvp.util.Toolkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class HitListener implements Listener {

	private final Resource config;

	/** Cached parse of the configured sound, rebuilt when the config value changes on reload. */
	private String cachedSoundString;
	private XSound.Record cachedSound;

	public HitListener(Game plugin) {
		this.config = plugin.getResources().getConfig();
	}

	@EventHandler(ignoreCancelled = true)
	public void onHit(EntityDamageByEntityEvent e) {
		if (!config.getBoolean("Combat.HitSound.Enabled")) {
			return;
		}

		if (e.getEntity() instanceof Player && e.getDamager() instanceof Player) {
			Player damager = (Player) e.getDamager();
			Player damagedPlayer = (Player) e.getEntity();

			if (Toolkit.inArena(damagedPlayer)) {
				XSound.Record hitSound = resolveHitSound();
				if (hitSound != null) {
					hitSound.soundPlayer().forPlayers(damagedPlayer, damager).play();
				}
			}
		}
	}

	private XSound.Record resolveHitSound() {
		int pitch = config.getInt("Combat.HitSound.Pitch");
		String soundString = config.fetchString("Combat.HitSound.Sound") + ", 1, " +
				Math.max(0.5f, Math.min(2.0f, pitch <= 0 ? 1.0f : (float) pitch));

		if (!soundString.equals(cachedSoundString)) {
			cachedSoundString = soundString;
			cachedSound = XSound.parse(soundString);

			if (cachedSound == null) {
				Toolkit.warnOnce("hitsound:" + soundString,
						"&7[&b&lKIT-PVP&7] &cCombat.HitSound.Sound in config.yml is not a sound this server " +
						"knows: " + config.fetchString("Combat.HitSound.Sound"));
			}
		}
		return cachedSound;
	}

}
