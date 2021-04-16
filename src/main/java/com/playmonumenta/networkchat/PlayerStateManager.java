package com.playmonumenta.networkchat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.event.PlayerSaveEvent;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public class PlayerStateManager implements Listener {
	private static final String IDENTIFIER = "NetworkChat";

	private static PlayerStateManager INSTANCE = null;
	private static Plugin mPlugin = null;
	private static Map<UUID, PlayerState> mPlayerStates = new HashMap<>();

	private PlayerStateManager(Plugin plugin) {
		INSTANCE = this;
		mPlugin = plugin;
	}

	public static PlayerStateManager getInstance() {
		return INSTANCE;
	}

	public static PlayerStateManager getInstance(Plugin plugin) {
		if (INSTANCE == null) {
			INSTANCE = new PlayerStateManager(plugin);
		}
		return INSTANCE;
	}

	public static Map<UUID, PlayerState> getPlayerStates() {
		return new HashMap<>(mPlayerStates);
	}

	public static PlayerState getPlayerState(Player player) {
		return mPlayerStates.get(player.getUniqueId());
	}

	public static void unregisterChannel(UUID channelId) {
		for (Map.Entry<UUID, PlayerState> playerStateEntry : mPlayerStates.entrySet()) {
			UUID playerId = playerStateEntry.getKey();
			Player player = Bukkit.getPlayer(playerId);
			PlayerState playerState = playerStateEntry.getValue();
			// TODO Make this fancier, and display the old name.
			player.sendMessage("Channel " + channelId.toString() + " has been removed.");
			playerState.unregisterChannel(channelId);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void playerJoinEvent(PlayerJoinEvent event) throws Exception {
		Player player = event.getPlayer();

		// Load player chat state, if it exists.
		JsonObject data = MonumentaRedisSyncAPI.getPlayerPluginData(player.getUniqueId(), IDENTIFIER);
		PlayerState playerState;
		if (data == null) {
			playerState = new PlayerState(player);
			mPlayerStates.put(player.getUniqueId(), playerState);
			mPlugin.getLogger().info("No chat state for for player " + player.getName());
		} else {
			try {
				playerState = PlayerState.fromJson(player, data);
				mPlayerStates.put(player.getUniqueId(), playerState);
				mPlugin.getLogger().info("Loaded chat state for player " + player.getName());
			} catch (Exception e) {
				playerState = new PlayerState(player);
				mPlayerStates.put(player.getUniqueId(), playerState);
				mPlugin.getLogger().warning("Player's chat state could not be loaded and was reset " + player.getName());
			}
		}

		for (UUID channelId : playerState.getWatchedChannelIds()) {
			ChannelManager.loadChannel(channelId, playerState);
		}
		for (UUID channelId : playerState.getUnwatchedChannelIds()) {
			ChannelManager.loadChannel(channelId, playerState);
		}
		UUID activeChannelId = playerState.getActiveChannelId();

		// TODO Send login messages here (once implemented)
	}

	/* Whenever player data is saved, also save the local data */
	@EventHandler(priority = EventPriority.MONITOR)
	public void playerSaveEvent(PlayerSaveEvent event) {
		Player player = event.getPlayer();

		PlayerState playerState = mPlayerStates.get(player.getUniqueId());
		if (playerState != null) {
			event.setPluginData(IDENTIFIER, playerState.toJson());
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void playerQuitEvent(PlayerQuitEvent event) throws Exception {
		Bukkit.getScheduler().runTaskLater(mPlugin, () -> {
			Player player = event.getPlayer();

			if (!player.isOnline()) {
				mPlayerStates.remove(player.getUniqueId());
			}
		}, 100);
	}
}
