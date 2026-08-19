package com.planetgallium.kitpvp.listener;

import com.planetgallium.kitpvp.game.Arena;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.util.Resources;
import com.planetgallium.kitpvp.util.Toolkit;

public class ChatListener implements Listener {

	// stand-ins for the two format arguments, chosen so they cannot appear in a
	// configured format or in a placeholder's output
	private static final String PLAYER_MARKER = "kpplayer";
	private static final String MESSAGE_MARKER = "kpmessage";

	private final Arena arena;
	private final Resources resources;

	public ChatListener(Game plugin) {
		this.arena = plugin.getArena();
		this.resources = plugin.getResources();
	}

	@EventHandler
	public void onChat(AsyncPlayerChatEvent e) {
		if (resources.getConfig().getBoolean("Chat.Enabled") && Toolkit.inArena(e.getPlayer())) {
			Player p = e.getPlayer();
			String levelPrefix = arena.getUtilities().getPlayerLevelPrefix(p.getName());

			String format = resources.getConfig().fetchString("Chat.Format")
					.replace("%player%", PLAYER_MARKER)
					.replace("%message%", MESSAGE_MARKER)
					.replace("%level%", levelPrefix);

			format = Toolkit.addPlaceholdersIfPossible(p, format);

			// the chat format is handed to String.format, so any other percent sign
			// in it, whether from the config or from a placeholder's output, has to
			// be escaped or the message throws instead of sending
			format = format.replace("%", "%%")
					.replace(PLAYER_MARKER, "%s")
					.replace(MESSAGE_MARKER, "%s");

			e.setFormat(format);
		}
	}

}
