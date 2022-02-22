package com.playmonumenta.networkchat;

import java.lang.ref.Cleaner;
import java.time.Instant;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkchat.utils.MessagingUtils;

import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class Message implements AutoCloseable {
	static class State implements Runnable {
		private final UUID mId;

		State(UUID id) {
			mId = id;
		}

		public void run() {
			MessageManager.unregisterMessage(mId);
		}
	}

	private final State mState;
	private final Cleaner.Cleanable mCleanable;

	private final UUID mId;
	private final Instant mInstant;
	private final UUID mChannelId;
	private final MessageType mMessageType;
	private final UUID mSenderId;
	private final String mSenderName;
	private final NamespacedKey mSenderType;
	private final boolean mSenderIsPlayer;
	private final Component mSenderComponent;
	private final JsonObject mExtraData;
	private final Component mMessage;
	private boolean mIsDeleted = false;

	private Message(UUID id,
	                Instant instant,
	                UUID channelId,
	                MessageType messageType,
	                UUID senderId,
	                String senderName,
	                NamespacedKey senderType,
	                boolean senderIsPlayer,
	                Component senderComponent,
	                JsonObject extraData,
	                Component message) {
		mId = id;
		mInstant = instant;
		mChannelId = channelId;
		mMessageType = messageType;
		mSenderId = senderId;
		mSenderName = senderName;
		mSenderType = senderType;
		mSenderIsPlayer = senderIsPlayer;
		mSenderComponent = senderComponent;
		mExtraData = extraData;
		mMessage = message;

		mState = new State(mId);
		mCleanable = MessageManager.cleaner().register(this, mState);
		MessageManager.registerMessage(this);
	}

	// Normally called through a channel
	protected static Message createMessage(Channel channel,
	                                       MessageType messageType,
	                                       CommandSender sender,
	                                       JsonObject extraData,
	                                       Component message) {
		UUID id = UUID.randomUUID();
		Instant instant = Instant.now();
		UUID channelId = channel.getUniqueId();
		UUID senderId = null;
		NamespacedKey senderType = null;
		if (sender instanceof Entity) {
			senderId = ((Entity) sender).getUniqueId();
			senderType = ((Entity) sender).getType().getKey();
		}
		boolean senderIsPlayer = sender instanceof Player;
		Component senderComponent = MessagingUtils.senderComponent(sender);
		message = NetworkChatPlugin.globalFilter().run(sender, message);
		return new Message(id,
		                   instant,
		                   channelId,
		                   messageType,
		                   senderId,
		                   sender.getName(),
		                   senderType,
		                   senderIsPlayer,
		                   senderComponent,
		                   extraData,
		                   message);
	}

	// Normally called through a channel
	protected static Message createMessage(Channel channel,
	                                       MessageType messageType,
	                                       CommandSender sender,
	                                       JsonObject extraData,
	                                       String message) {
		Component messageComponent = MessagingUtils.getAllowedMiniMessage(sender).parse(message);
		return Message.createMessage(channel, messageType, sender, extraData, messageComponent);
	}

	// Raw, non-channel messages (use sparingly)
	protected static Message createRawMessage(MessageType messageType,
	                                          UUID senderId,
	                                          JsonObject extraData,
	                                          Component message) {
		UUID id = UUID.randomUUID();
		Instant instant = Instant.now();
		UUID channelId = null;
		if (senderId != null) {
			if (senderId.getMostSignificantBits() == 0 && senderId.getLeastSignificantBits() == 0) {
				senderId = null;
			}
		}
		NamespacedKey senderType = null;
		boolean senderIsPlayer = (senderId != null);
		String senderName = "";
		Component senderComponent = Component.empty();
		return new Message(id,
		                   instant,
		                   channelId,
		                   messageType,
		                   senderId,
		                   senderName,
		                   senderType,
		                   senderIsPlayer,
		                   senderComponent,
		                   extraData,
		                   message);
	}

	// For when receiving remote messages
	protected static Message fromJson(JsonObject object) {
		UUID id = null;
		if (object.get("id") != null) {
			id = UUID.fromString(object.get("id").getAsString());
		}
		Message existingMessage = MessageManager.getMessage(id);
		if (existingMessage != null) {
			return existingMessage;
		}

		Instant instant = Instant.ofEpochMilli(object.get("instant").getAsLong());
		UUID channelId = null;
		if (object.get("channelId") != null) {
			channelId = UUID.fromString(object.get("channelId").getAsString());
		}
		MessageType messageType = MessageType.CHAT;
		if (object.get("messageType") != null) {
			String messageTypeName = object.get("messageType").getAsString();
			for (MessageType possibleType : MessageType.values()) {
				if (possibleType.name().equals(messageTypeName)) {
					messageType = possibleType;
					break;
				}
			}
		}
		UUID senderId = null;
		if (object.get("senderId") != null) {
			senderId = UUID.fromString(object.get("senderId").getAsString());
		}
		String senderName = object.get("senderName").getAsString();
		NamespacedKey senderType = null;
		if (object.get("senderType") != null) {
			senderType = NamespacedKey.fromString(object.get("senderType").getAsString());
		}
		boolean senderIsPlayer = object.get("senderIsPlayer").getAsBoolean();
		Component senderComponent = MessagingUtils.fromJson(object.get("senderComponent"));
		JsonObject extraData = null;
		if (object.get("extra") != null) {
			extraData = object.get("extra").getAsJsonObject();
		}
		Component message = GsonComponentSerializer.gson().deserializeFromTree(object.get("message"));

		return new Message(id,
		                   instant,
		                   channelId,
		                   messageType,
		                   senderId,
		                   senderName,
		                   senderType,
		                   senderIsPlayer,
		                   senderComponent,
		                   extraData,
		                   message);
	}

	protected JsonObject toJson() {
		JsonObject object = new JsonObject();

		object.addProperty("id", mId.toString());
		object.addProperty("instant", mInstant.toEpochMilli());
		if (mChannelId != null) {
			object.addProperty("channelId", mChannelId.toString());
		}
		object.addProperty("messageType", mMessageType.name());
		if (mSenderId != null) {
			object.addProperty("senderId", mSenderId.toString());
		}
		object.addProperty("senderName", mSenderName);
		if (mSenderType != null) {
			object.addProperty("senderType", mSenderType.toString());
		}
		object.addProperty("senderIsPlayer", mSenderIsPlayer);
		object.add("senderComponent", MessagingUtils.toJson(mSenderComponent));
		if (mExtraData != null) {
			object.add("extra", mExtraData);
		}
		object.add("message", GsonComponentSerializer.gson().serializeToTree(mMessage));

		return object;
	}

	public void close() {
		mCleanable.clean();
	}

	public UUID getUniqueId() {
		return mId;
	}

	public String getGuiCommand() {
		if (mId == null) {
			return "/chat gui";
		}
		return "/chat gui message " + mId.toString();
	}

	public ClickEvent getGuiClickEvent() {
		return ClickEvent.runCommand(getGuiCommand());
	}

	public Instant getInstant() {
		return mInstant;
	}

	public UUID getChannelUniqueId() {
		return mChannelId;
	}

	public Channel getChannel() {
		return ChannelManager.getChannel(mChannelId);
	}

	public JsonObject getExtraData() {
		return mExtraData;
	}

	public MessageType getMessageType() {
		return mMessageType;
	}

	public UUID getSenderId() {
		return mSenderId;
	}

	public String getSenderName() {
		return mSenderName;
	}

	public Component getSenderComponent() {
		return mSenderComponent;
	}

	public NamespacedKey getSenderType() {
		return mSenderType;
	}

	public boolean senderIsPlayer() {
		return mSenderIsPlayer;
	}

	public Component getMessage() {
		return mMessage;
	}

	public String getPlainMessage() {
		return MessagingUtils.plainText(mMessage);
	}

	public boolean isDeleted() {
		return mIsDeleted;
	}

	// Must be called from PlayerState to allow pausing messages.
	protected void showMessage(CommandSender recipient) {
		if (mIsDeleted) {
			return;
		}
		Channel channel = ChannelManager.getChannel(mChannelId);
		if (channel == null) {
			// Non-channel messages
			if (mExtraData != null) {
				JsonPrimitive shardJson = mExtraData.getAsJsonPrimitive("shard");
				if (shardJson != null) {
					String shard = shardJson.getAsString();
					if (!shard.equals("*") && !shard.equals(RemotePlayerManager.getShardName())) {
						return;
					}
				}
			}
			Identity senderId = Identity.nil();
			if (mSenderId != null) {
				senderId = Identity.identity(mSenderId);
			}
			recipient.sendMessage(senderId, mMessage, mMessageType);
			return;
		}
		channel.showMessage(recipient, this);
	}

	protected void markDeleted() {
		mIsDeleted = true;
	}
}
