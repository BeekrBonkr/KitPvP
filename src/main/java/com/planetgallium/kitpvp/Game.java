package com.planetgallium.kitpvp;

import com.planetgallium.kitpvp.game.CombatTagger;
import com.planetgallium.kitpvp.game.Infobase;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.planetgallium.kitpvp.api.EventListener;
import com.planetgallium.kitpvp.command.*;
import com.planetgallium.kitpvp.game.Arena;
import com.planetgallium.kitpvp.listener.*;
import com.planetgallium.kitpvp.util.*;

public class Game extends JavaPlugin implements Listener {

	private static Game instance;
	private static String prefix = "None";

	private Arena arena;
	private Infobase database;
	private Resources resources;
	private CombatTagger combatTagger;
	private MainCommand mainCommand;
	private TrackerListener trackerListener;

	private String updateVersion = "Error";
	private boolean needsUpdate = false;
	private boolean hasPlaceholderAPI = false;
	private boolean hasWorldGuard = false;

	@Override
	public void onEnable() {
		Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &7Enabling &bKitPvP &7version &b" + this.getDescription().getVersion() + "&7...");

		instance = this;
		resources = new Resources(this);
		prefix = resources.getMessages().fetchString("Messages.General.Prefix");
		database = new Infobase(this);
		arena = new Arena(this, resources);
		combatTagger = new CombatTagger(this);
		arena.getKits().validateLoadedKits();

		trackerListener = new TrackerListener(this);
		mainCommand = new MainCommand(this);

		PluginManager pm = Bukkit.getPluginManager();
		pm.registerEvents(this, this);
		pm.registerEvents(new EventListener(this), this);
		pm.registerEvents(new ArenaListener(this), this);
		pm.registerEvents(new JoinListener(this), this);
		pm.registerEvents(new LeaveListener(this), this);
		pm.registerEvents(new ArrowListener(this), this);
		pm.registerEvents(new DeathListener(this), this);
		pm.registerEvents(new HitListener(this), this);
		pm.registerEvents(new ItemListener(this), this);
		pm.registerEvents(new SoupListener(this), this);
		pm.registerEvents(new ChatListener(this), this);
		pm.registerEvents(new SignListener(this), this);
		pm.registerEvents(new AliasCommand(this), this);
		pm.registerEvents(new AbilityListener(this), this);
		pm.registerEvents(trackerListener, this);
		pm.registerEvents(new MenuListener(this), this);
		pm.registerEvents(combatTagger, this);
		pm.registerEvents(getArena().getKillStreaks(), this);

		getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

		PluginCommand mainPluginCommand = getCommand("kitpvp");
		if (mainPluginCommand != null) {
			mainPluginCommand.setExecutor(mainCommand);
			mainPluginCommand.setTabCompleter(new MainTabCompleter(this));
		}

		combatTagger.start();
		arena.startScoreboardRefresh();

		new Metrics(this);

		new BukkitRunnable() {
			@Override
			public void run() {
				checkUpdate();
			}
		}.runTaskAsynchronously(this);

		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			Bukkit.getConsoleSender().sendMessage(Toolkit.translate("[&b&lKIT-PVP&7] &7Hooking into &bPlaceholderAPI&7..."));
			new Placeholders(this).register();
			hasPlaceholderAPI = true;
		}

		if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
			Bukkit.getConsoleSender().sendMessage(Toolkit.translate("[&b&lKIT-PVP&7] &7Hooking into &bWorldGuard&7..."));
			hasWorldGuard = true;
		}

		populateUUIDCacheForOnlinePlayers();

		Toolkit.printToConsole("&7[&b&lKIT-PVP&7] &aDone!");
	}

	private void populateUUIDCacheForOnlinePlayers() {
		// populates UUID cache if there are players online when doing /reload to avoid a lot of errors related
		// to database and UUIDs
		for (Player player : Bukkit.getOnlinePlayers()) {
			CacheManager.getUUIDCache().put(player.getName(), player.getUniqueId().toString());
			arena.getStats().createPlayer(player);
		}
	}

	private void checkUpdate() {
		Updater.of(this).resourceId(27107).handleResponse((versionResponse, version) -> {
			switch (versionResponse) {
				case FOUND_NEW:
					Bukkit.getConsoleSender().sendMessage(Toolkit.translate("&7[&b&lKIT-PVP&7] &aNew version found! Please update to v" + version + " on the Spigot page."));
					needsUpdate = true;
					updateVersion = version;
					break;
				case UNAVAILABLE:
					Bukkit.getConsoleSender().sendMessage(Toolkit.translate("&7[&b&lKIT-PVP&7] &cUnable to perform an update check."));
					break;
			}
		}).check();
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		Player p = e.getPlayer();

		if (!resources.getConfig().contains("Items.Leave") ||
				!resources.getConfig().getBoolean("Items.Leave.Enabled")) {
			return;
		}

		if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}

		if (Toolkit.matchesConfigItem(Toolkit.getHandItemForInteraction(e), resources.getConfig(), "Items.Leave")) {
			e.setCancelled(true);

			// leaving for the hub is another way out of a fight
			if (!p.hasPermission("kp.combattag.bypass") && combatTagger.denyIfTagged(p)) {
				return;
			}

			if (resources.getConfig().getBoolean("Items.Leave.BungeeCord.Enabled")) {
				ByteArrayDataOutput out = ByteStreams.newDataOutput();
				out.writeUTF("Connect");

				String server = resources.getConfig().fetchString("Items.Leave.BungeeCord.Server");

				out.writeUTF(server);
				p.sendPluginMessage(this, "BungeeCord", out.toByteArray());
			}
		}
	}

	@EventHandler
	public void onQuitCleanup(PlayerQuitEvent e) {
		mainCommand.cancelSpawnCountdown(e.getPlayer().getUniqueId());
	}

	@Override
	public void onDisable() {
		if (combatTagger != null) {
			combatTagger.stop();
		}
		if (trackerListener != null) {
			trackerListener.stopAll();
		}
		if (arena != null) {
			arena.stopScoreboardRefresh();

			// players still online at shutdown have unsaved stats in the cache
			for (Player player : Bukkit.getOnlinePlayers()) {
				arena.getStats().pushCachedStatsToDatabaseNow(player.getName());
			}
		}
	}

	public boolean hasPlaceholderAPI() { return hasPlaceholderAPI; }

	public boolean hasWorldGuard() { return hasWorldGuard; }

	public boolean needsUpdate() { return needsUpdate; }

	public String getUpdateVersion() { return updateVersion; }

	public static Game getInstance() { return instance; }

	public Arena getArena() { return arena; }

	public CombatTagger getCombatTagger() { return combatTagger; }

	public Infobase getDatabase() { return database; }

	public static String getPrefix() { return prefix; }

	public Resources getResources() { return resources; }

}
