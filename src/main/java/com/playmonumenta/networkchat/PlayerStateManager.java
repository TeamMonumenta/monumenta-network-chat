package com.playmonumenta.networkchat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.event.PlayerSaveEvent;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;

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
	private static boolean mIsDefaultChatPlugin = true;

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

	public static boolean isDefaultChat() {
		return mIsDefaultChatPlugin;
	}

	public static void isDefaultChat(boolean value) {
		mIsDefaultChatPlugin = value;
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
		for (UUID channelId : playerState.getWhisperChannelIds()) {
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
			UUID playerId = player.getUniqueId();

			if (!player.isOnline()) {
				PlayerState oldState = mPlayerStates.get(playerId);
				mPlayerStates.remove(playerId);
				if (oldState != null) {
					for (UUID channelId : oldState.getWatchedChannelIds()) {
						Channel channel = ChannelManager.getChannel(channelId);
						// This conveniently only unloads channels if they're not in use.
						ChannelManager.unloadChannel(channel);
					}
				}
			}
		}, 100);
	}

	@EventHandler(priority = EventPriority.LOW)
	public void asyncChatEvent(AsyncChatEvent event) throws Exception {
		if (event.isCancelled()) {
			return;
		}
		if (!mIsDefaultChatPlugin) {
			return;
		}

		Player player = event.getPlayer();
		PlayerState playerState = mPlayerStates.get(player.getUniqueId());
		if (playerState == null) {
			player.sendMessage(Component.text("You have no chat state and cannot talk. Please report this bug.", NamedTextColor.RED));
			return;
		}

		Component message = event.message();
		String messageStr = PlainComponentSerializer.plain().serialize(message);

		Channel channel = playerState.getActiveChannel();
		if (channel == null) {
			player.sendMessage(Component.text("You have no active channel. Please set one with /chattest and try again.", NamedTextColor.RED));
			return;
		}
		channel.sendMessage(player, messageStr);

		event.setCancelled(true);
	}
}