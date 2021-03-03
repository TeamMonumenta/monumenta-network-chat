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

	@EventHandler(priority = EventPriority.LOW)
	public void playerJoinEvent(PlayerJoinEvent event) throws Exception {
		Player player = event.getPlayer();

		// Load player chat state, if it exists.
		JsonObject data = MonumentaRedisSyncAPI.getPlayerPluginData(player.getUniqueId(), IDENTIFIER);
		PlayerState playerState;
		if (data == null) {
			playerState = new PlayerState(player);
			mPlayerStates.put(player.getUniqueId(), playerState);
			mPlugin.getLogger().info("No data for for player " + player.getName());
		} else {
			playerState = PlayerState.fromJson(player, data);
			mPlayerStates.put(player.getUniqueId(), playerState);
			mPlugin.getLogger().info("Loaded data for player " + player.getName());
		}

		// TODO Load channels from Redis, and failing that revoke those channels. Placeholder:
		Set<UUID> loadedChannels = ChannelManager.getLoadedChannelIds();
		boolean channelDeleted = false;
		for (UUID channelId : playerState.getWatchedChannelIds()) {
			if (channelId != null && !loadedChannels.contains(channelId)) {
				channelDeleted = true;
				playerState.unregisterChannel(channelId);
			}
		}
		for (UUID channelId : playerState.getUnwatchedChannelIds()) {
			if (channelId != null && !loadedChannels.contains(channelId)) {
				channelDeleted = true;
				playerState.unregisterChannel(channelId);
			}
		}
		UUID activeChannelId = playerState.getActiveChannelId();
		if (activeChannelId != null && !loadedChannels.contains(activeChannelId)) {
			channelDeleted = true;
			playerState.unregisterChannel(activeChannelId);
		}
		if (channelDeleted) {
			player.sendMessage("One or more channels are no longer available.");
		}
		// End of channel loading placeholder

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
