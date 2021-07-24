package com.playmonumenta.networkchat;

import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class MessageManager implements Listener {
	public static final String NETWORK_CHAT_MESSAGE = "com.playmonumenta.networkchat.Message";

	private static MessageManager INSTANCE = null;
	private static Plugin mPlugin = null;
	private static Cleaner mCleaner = Cleaner.create();
	private static Map<UUID, WeakReference<Message>> mMessages = new HashMap<>();

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

	public static Message getMessage(UUID messageId) {
		WeakReference<Message> messageWeakReference = mMessages.get(messageId);
		if (messageWeakReference == null) {
			return null;
		}
		return messageWeakReference.get();
	}

	public void broadcastMessage(Message message) throws Exception {
		NetworkRelayAPI.sendExpiringBroadcastMessage(NETWORK_CHAT_MESSAGE,
		                                             message.toJson(),
		                                             NetworkChatPlugin.getMessageTtl());
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

		Channel channel = message.getChannel();
		if (channel == null) {
			ChannelManager.loadChannel(message.getChannelUniqueId(), message);
		} else {
			channel.distributeMessage(message);
		}
	}

	// Internal use only; used to clean up registered Messages.
	protected static Cleaner cleaner() {
		return mCleaner;
	}

	// Internal use only; register a weak reference to a Message for command use
	protected static void registerMessage(Message message) {
		UUID messageId = message.getUniqueId();
		if (messageId == null) {
			return;
		}
		if (mMessages.containsKey(messageId)) {
			mPlugin.getLogger().severe("Attempting to register previously registered message ID!");
		}
		mMessages.put(messageId, new WeakReference<Message>(message));
		mPlugin.getLogger().finest(() -> "New message ID " + messageId.toString()
		                                 + ", tracked message IDs: " + Integer.toString(mMessages.size()));
	}

	// Internal use only; unregister a weak reference to a Message when the Message is finalized
	protected static void unregisterMessage(UUID messageId) {
		if (messageId == null) {
			return;
		}
		mPlugin.getLogger().finest(() -> "unregistering message ID " + messageId.toString()
		                                 + ", tracked message IDs: " + Integer.toString(mMessages.size()));
		mMessages.remove(messageId);
	}
}
