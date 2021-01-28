package com.playmonumenta.networkchat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class ChatManager implements Listener {
	public static final String NETWORK_CHAT_MESSAGE = "Monumenta.Broadcast.ChatMessage";

	private static ChatManager INSTANCE = null;
	private static Plugin mPlugin = null;
	protected static Map<String, Map<String, ChatChannelBase>> mChannels = new HashMap<String, Map<String, ChatChannelBase>>();
	protected static Map<Player, PlayerChatState> mPlayerStates = new HashMap<Player, PlayerChatState>();

	private ChatManager(Plugin plugin) {
		INSTANCE = this;
		mPlugin = plugin;

		// Add empty map for each channel class
		mChannels.put(ChatChannelLocal.getChannelClassId(), new HashMap<String, ChatChannelBase>());
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

	public static PlayerChatState getPlayerState(Player player) {
		return mPlayerStates.get(player);
	}

	public static Set<String> getChannelClassIds() {
		return new HashSet<String>(mChannels.keySet());
	}

	public static Set<String> getChannelIds(String channelClassId) {
		Map<String, ChatChannelBase> channelMap = mChannels.get(channelClassId);

		if (channelMap == null) {
			return new HashSet<String>();
		}
		return channelMap.keySet();
	}

	public static void registerNewChannel(ChatChannelBase channel) {
		if (channel == null) {
			return;
		}

		String channelClassId = channel.getChannelClassId();
		Map<String, ChatChannelBase> channelMap = mChannels.get(channelClassId);
		if (channelMap == null) {
			// TODO Log this. This should never happen. It should be created above.
			channelMap = new HashMap<>();
			mChannels.put(channelClassId, channelMap);
		}

		String channelId = channel.getChannelId();
		ChatChannelBase oldChannel = channelMap.get(channelId);
		if (oldChannel != null) {
			// TODO Channel already exists! What now?
			return;
		}
		channelMap.put(channelId, channel);
	}

	public static ChatChannelBase getChannel(String channelClassId, String channelId) {
		if (channelClassId == null || channelId == null) {
			return null;
		}

		Map<String, ChatChannelBase> channelMap = mChannels.get(channelClassId);
		if (channelMap == null) {
			return null;
		}

		return channelMap.get(channelId);
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
}
