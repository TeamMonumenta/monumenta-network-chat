package com.playmonumenta.networkchat;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class MessageManager implements Listener {
	public static final String NETWORK_CHAT_MESSAGE = "Monumenta.Broadcast.ChatMessage";

	private static MessageManager INSTANCE = null;
	private static Plugin mPlugin = null;

	private MessageManager(Plugin plugin) {
		INSTANCE = this;
		mPlugin = plugin;
	}

	public static MessageManager getInstance() {
		return INSTANCE;
	}

	public static MessageManager getInstance(Plugin plugin) {
		if (INSTANCE == null) {
			INSTANCE = new MessageManager(plugin);
		}
		return INSTANCE;
	}

	public void broadcastMessage(Message message) throws Exception {
		String shardName = NetworkRelayAPI.getShardName();
		NetworkRelayAPI.sendBroadcastMessage(NETWORK_CHAT_MESSAGE, message.toJson());
	}

	@EventHandler(priority = EventPriority.LOW)
	public void networkRelayMessageEvent(NetworkRelayMessageEvent event) {
		switch (event.getChannel()) {
		case NETWORK_CHAT_MESSAGE:
			JsonObject data = event.getData();
			if (data == null) {
				mPlugin.getLogger().severe("Got " + NETWORK_CHAT_MESSAGE + " message with null data");
				return;
			}
			receiveMessage(data);
			break;
		default:
			break;
		}
	}

	public void receiveMessage(JsonObject object) {
		Message message = null;
		try {
			message = Message.fromJson(object);
		} catch (Exception e) {
			mPlugin.getLogger().severe("Could not read Message from json:");
			mPlugin.getLogger().severe(e.getMessage());
			return;
		}

		ChannelBase channel = message.getChannel();
		if (channel == null) {
			ChannelManager.loadChannel(message.getChannelUniqueId(), message);
		} else {
			channel.distributeMessage(message);
		}
	}
}
