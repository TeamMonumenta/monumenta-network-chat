package com.playmonumenta.networkchat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public class ChatManager implements Listener {
	public static final String NETWORK_CHAT_MESSAGE = "Monumenta.Broadcast.ChatMessage";

	private static ChatManager INSTANCE = null;
	private static Plugin mPlugin = null;
	protected static Map<UUID, ChatChannelBase> mChannels = new HashMap<>();
	protected static Map<String, ChatChannelBase> mChannelsByName = new HashMap<>();
	protected static Map<Player, PlayerChatState> mPlayerStates = new HashMap<>();

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

	public static Map<Player, PlayerChatState> getPlayerStates() {
		return mPlayerStates;
	}

	public static PlayerChatState getPlayerState(Player player) {
		return mPlayerStates.get(player);
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

		// TODO Load player chat state, preferably before they log in.

		mPlayerStates.put(player, new PlayerChatState(player));
	}

	@EventHandler(priority = EventPriority.LOW)
	public void playerQuitEvent(PlayerQuitEvent event) throws Exception {
		Player player = event.getPlayer();

		// TODO Save player chat state

		mPlayerStates.remove(player);
	}
}
