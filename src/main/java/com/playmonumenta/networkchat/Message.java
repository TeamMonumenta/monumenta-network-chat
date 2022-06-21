package com.playmonumenta.networkchat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import java.lang.ref.Cleaner;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
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

	// Member variable used to garbage collect Message objects
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
	protected static @Nullable Message createMessage(Channel channel,
	                                       MessageType messageType,
	                                       CommandSender sender,
	                                       JsonObject extraData,
	                                       Component message) {
		UUID id = UUID.randomUUID();
		Instant instant = Instant.now();
		UUID channelId = channel.getUniqueId();
		@Nullable UUID senderId = null;
		@Nullable NamespacedKey senderType = null;
		if (sender instanceof Entity) {
			senderId = ((Entity) sender).getUniqueId();
			senderType = ((Entity) sender).getType().getKey();
		}
		boolean senderIsPlayer = sender instanceof Player;
		Component senderComponent = MessagingUtils.senderComponent(sender);
		ChatFilter.ChatFilterResult filterResult = new ChatFilter.ChatFilterResult(message);
		NetworkChatPlugin.globalFilter().run(sender, filterResult);
		message = filterResult.component();
		if (filterResult.foundBadWord()) {
			sender.sendMessage(Component.text("You cannot say that on this server:", NamedTextColor.RED));
			sender.sendMessage(message);
			return null;
		}
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
	protected static @Nullable Message createMessage(Channel channel,
	                                       MessageType messageType,
	                                       CommandSender sender,
	                                       JsonObject extraData,
	                                       String message) {
		Component messageComponent = MessagingUtils.getAllowedMiniMessage(sender).deserialize(message);
		return Message.createMessage(channel, messageType, sender, extraData, messageComponent);
	}

	// Raw, non-channel messages (use sparingly)
	protected static Message createRawMessage(MessageType messageType,
	                                          @Nullable UUID senderId,
	                                          JsonObject extraData,
	                                          Component message) {
		UUID id = UUID.randomUUID();
		Instant instant = Instant.now();
		if (senderId != null) {
			if (senderId.getMostSignificantBits() == 0 && senderId.getLeastSignificantBits() == 0) {
				senderId = null;
			}
		}
		@Nullable NamespacedKey senderType = null;
		if (senderId != null) {
			@Nullable Entity sender = Bukkit.getEntity(senderId);
			if (sender != null) {
				senderType = sender.getType().getKey();
			}
		}
		boolean senderIsPlayer = (senderId != null);
		String senderName = "";
		Component senderComponent = Component.empty();
		return new Message(id,
		                   instant,
		                   null,
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
		@Nullable UUID id = null;
		@Nullable JsonElement idJson = object.get("id");
		if (idJson != null) {
			id = UUID.fromString(idJson.getAsString());
		}
		@Nullable Message existingMessage = MessageManager.getMessage(id);
		if (existingMessage != null) {
			return existingMessage;
		}

		Instant instant;
		try {
			instant = Instant.ofEpochMilli(object.get("instant").getAsLong());
		} catch (Exception ex) {
			instant = Instant.EPOCH;
		}
		@Nullable UUID channelId = null;
		@Nullable JsonElement channelIdJson = object.get("channelId");
		if (channelIdJson != null) {
			channelId = UUID.fromString(channelIdJson.getAsString());
		}
		MessageType messageType = MessageType.CHAT;
		@Nullable JsonElement messageTypeJson = object.get("messageType");
		if (messageTypeJson != null) {
			String messageTypeName = messageTypeJson.getAsString();
			for (MessageType possibleType : MessageType.values()) {
				if (possibleType.name().equals(messageTypeName)) {
					messageType = possibleType;
					break;
				}
			}
		}
		@Nullable UUID senderId = null;
		@Nullable JsonElement senderIdJson = object.get("senderId");
		if (senderIdJson != null) {
			senderId = UUID.fromString(senderIdJson.getAsString());
		}
		@Nullable JsonElement senderNameJson = object.get("senderName");
		String senderName;
		if (senderNameJson == null) {
			senderName = "@";
		} else {
			senderName = senderNameJson.getAsString();
		}
		@Nullable NamespacedKey senderType = null;
		@Nullable JsonElement senderTypeJson = object.get("senderType");
		if (senderTypeJson != null) {
			senderType = NamespacedKey.fromString(senderTypeJson.getAsString());
		}
		@Nullable JsonElement senderIsPlayerJson = object.get("senderIsPlayer");
		boolean senderIsPlayer;
		if (senderIsPlayerJson == null) {
			senderIsPlayer = false;
		} else {
			senderIsPlayer = senderIsPlayerJson.getAsBoolean();
		}
		Component senderComponent = Component.text(senderName);
		@Nullable JsonElement senderComponentJson = object.get("senderComponent");
		if (senderComponentJson != null) {
			senderComponent = MessagingUtils.fromJson(senderComponentJson);
		}
		@Nullable JsonObject extraData = null;
		@Nullable JsonElement extraJson = object.get("extra");
		if (extraJson != null) {
			extraData = extraJson.getAsJsonObject();
		}
		@Nullable JsonElement messageJson = object.get("message");
		Component message;
		if (messageJson == null) {
			message = Component.text("[MESSAGE COULD NOT BE LOADED]", NamedTextColor.RED, TextDecoration.BOLD);
		} else {
			message = GsonComponentSerializer.gson().deserializeFromTree(messageJson);
		}

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
		return "/chat gui message " + mId;
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

	public Identity getSenderIdentity() {
		if (mSenderIsPlayer && mSenderId != null) {
			return Identity.identity(mSenderId);
		}
		return Identity.nil();
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

	// Get the message as shown to a given recipient
	public Component shownMessage(CommandSender recipient) {
		if (mIsDeleted) {
			return Component.text("[DELETED]", NamedTextColor.RED, TextDecoration.BOLD);
		}
		@Nullable Channel channel = ChannelManager.getChannel(mChannelId);
		if (channel == null) {
			// Non-channel messages
			if (mExtraData != null) {
				@Nullable JsonPrimitive shardJson = mExtraData.getAsJsonPrimitive("shard");
				if (shardJson != null) {
					String shard = shardJson.getAsString();
					if (!shard.equals("*") && !shard.equals(RemotePlayerManager.getShardName())) {
						return Component.text("[NOT VISIBLE ON THIS SHARD]", NamedTextColor.RED, TextDecoration.BOLD);
					}
				}
			}
			return mMessage;
		}
		return channel.shownMessage(recipient, this);
	}

	// Must be called from PlayerState to allow pausing messages.
	protected void showMessage(CommandSender recipient) {
		if (mIsDeleted) {
			return;
		}
		@Nullable Channel channel = ChannelManager.getChannel(mChannelId);
		if (channel == null) {
			// Non-channel messages
			if (mExtraData != null) {
				@Nullable JsonPrimitive shardJson = mExtraData.getAsJsonPrimitive("shard");
				if (shardJson != null) {
					String shard = shardJson.getAsString();
					if (!shard.equals("*") && !shard.equals(RemotePlayerManager.getShardName())) {
						return;
					}
				}
			}
			recipient.sendMessage(getSenderIdentity(), mMessage, mMessageType);
			return;
		}
		channel.showMessage(recipient, this);
	}

	protected void markDeleted() {
		mIsDeleted = true;
	}
}
