package com.playmonumenta.networkchat;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
	protected static Map<String, ChatChannelBase> mChannelClasses = new HashMap<String, ChatChannelBase>();
	protected static Map<Player, PlayerChatState> mPlayerStates = new HashMap<Player, PlayerChatState>();

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
		}
	}
}
