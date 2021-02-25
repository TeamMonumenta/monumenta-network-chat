package com.playmonumenta.networkchat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.event.PlayerSaveEvent;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public class ChatManager implements Listener {
	private static final String IDENTIFIER = "NetworkChat";
	public static final String NETWORK_CHAT_MESSAGE = "Monumenta.Broadcast.ChatMessage";

	private static ChatManager INSTANCE = null;
	private static Plugin mPlugin = null;
	private static Map<UUID, ChatChannelBase> mChannels = new HashMap<>();
	private static Map<String, ChatChannelBase> mChannelsByName = new HashMap<>();
	private static Map<UUID, PlayerChatState> mPlayerStates = new HashMap<>();

	private ChatManager(Plugin plugin) {
		INSTANCE = this;
		mPlugin = plugin;
	}

	public static ChatManager getInstance() {
		return INSTANCE;
	}

	public static ChatManager getInstance(Plugin plugin) {
		if (INSTANCE == null) {
			INSTANCE = new ChatManager(plugin);
		}
		return INSTANCE;
	}

	public static Map<UUID, PlayerChatState> getPlayerStates() {
		return new HashMap<>(mPlayerStates);
	}

	public static PlayerChatState getPlayerState(Player player) {
		return mPlayerStates.get(player.getUniqueId());
	}

	public static Set<String> getChannelNames() {
		return new HashSet<>(mChannelsByName.keySet());
	}

	public static void registerNewChannel(ChatChannelBase channel) throws WrapperCommandSyntaxException {
		if (channel == null) {
			return;
		}

		String channelName = channel.getName();
		ChatChannelBase oldChannel = mChannelsByName.get(channelName);
		if (oldChannel != null) {
			CommandAPI.fail("Channel " + channelName + " already exists!");
		}
		UUID uuid = channel.getUniqueId();
		mChannels.put(uuid, channel);
		mChannelsByName.put(channelName, channel);
	}

	public static ChatChannelBase getChannel(UUID channelUuid) {
		return mChannels.get(channelUuid);
	}

	public static ChatChannelBase getChannel(String channelName) {
		return mChannelsByName.get(channelName);
	}

	public static void renameChannel(String oldName, String newName) throws WrapperCommandSyntaxException {
		ChatChannelBase channel = mChannelsByName.get(oldName);
		if (channel == null) {
			CommandAPI.fail("Channel " + oldName + " does not exist!");
		}

		if (mChannelsByName.get(newName) != null) {
			CommandAPI.fail("Channel " + newName + " already exists!");
		}

		// NOTE: May call CommandAPI.fail to cancel the change before it occurs.
		channel.setName(newName);

		mChannelsByName.put(newName, channel);
		mChannelsByName.remove(oldName);

		// TODO Broadcast and save the change
	}

	public static void deleteChannel(String channelName) throws WrapperCommandSyntaxException {
		ChatChannelBase channel = mChannelsByName.get(channelName);
		UUID channelId = channel.getUniqueId();
		if (channel == null) {
			CommandAPI.fail("Channel " + channelName + " does not exist!");
		}

		for (PlayerChatState playerState : mPlayerStates.values()) {
			playerState.unregisterChannel(channelId);
		}

		mChannels.remove(channelId);
		mChannelsByName.remove(channelName);

		// TODO Broadcast and save the change
	}

	@EventHandler(priority = EventPriority.LOW)
	public void networkRelayMessageEvent(NetworkRelayMessageEvent event) {
		switch (event.getChannel()) {
		case NETWORK_CHAT_MESSAGE:
			JsonObject data = event.getData();
			if (data == null) {
				mPlugin.getLogger().severe("Got " + NETWORK_CHAT_MESSAGE + " message with null data)");
				return;
			}
			// TODO
			break;
		default:
			break;
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void playerJoinEvent(PlayerJoinEvent event) throws Exception {
		Player player = event.getPlayer();

		// Load player chat state, if it exists.
		JsonObject data = MonumentaRedisSyncAPI.getPlayerPluginData(player.getUniqueId(), IDENTIFIER);
		PlayerChatState playerState;
		if (data == null) {
			playerState = new PlayerChatState(player);
			mPlayerStates.put(player.getUniqueId(), playerState);
			mPlugin.getLogger().info("No data for for player " + player.getName());
		} else {
			playerState = PlayerChatState.fromJson(player, data);
			mPlayerStates.put(player.getUniqueId(), playerState);
			mPlugin.getLogger().info("Loaded data for player " + player.getName());
		}

		// TODO Load channels from Redis, and failing that revoke those channels. Placeholder:
		boolean channelDeleted = false;
		for (UUID channelId : playerState.getWatchedChannelIds()) {
			if (channelId != null && !mChannels.containsKey(channelId)) {
				channelDeleted = true;
				playerState.unregisterChannel(channelId);
			}
		}
		for (UUID channelId : playerState.getUnwatchedChannelIds()) {
			if (channelId != null && !mChannels.containsKey(channelId)) {
				channelDeleted = true;
				playerState.unregisterChannel(channelId);
			}
		}
		UUID activeChannelId = playerState.getActiveChannelId();
		if (activeChannelId != null && !mChannels.containsKey(activeChannelId)) {
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

		PlayerChatState playerState = mPlayerStates.get(player.getUniqueId());
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
