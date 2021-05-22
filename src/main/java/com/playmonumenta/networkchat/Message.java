package com.playmonumenta.networkchat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.transformation.TransformationType;
import net.kyori.adventure.text.minimessage.markdown.DiscordFlavor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class Message {
	private final Instant mInstant;
	private final UUID mChannelId;
	private final UUID mSenderId;
	private final String mSenderName;
	private final NamespacedKey mSenderType;
	private final boolean mSenderIsPlayer;
	private final JsonObject mExtraData;
	private final Component mMessage;
	private boolean mIsDeleted = false;

	private Message(Instant instant, UUID channelId, UUID senderId, String senderName, NamespacedKey senderType, boolean senderIsPlayer, JsonObject extraData, Component message) {
		mInstant = instant;
		mChannelId = channelId;
		mSenderId = senderId;
		mSenderName = senderName;
		mSenderType = senderType;
		mSenderIsPlayer = senderIsPlayer;
		mExtraData = extraData;
		mMessage = message;
	}

	// Normally called through a channel
	protected static Message createMessage(Channel channel, CommandSender sender, JsonObject extraData, Component message) {
		Instant instant = Instant.now();
		UUID channelId = channel.getUniqueId();
		UUID senderId = null;
		NamespacedKey senderType = null;
		if (sender instanceof Entity) {
			senderId = ((Player) sender).getUniqueId();
			senderType = ((Entity) sender).getType().getKey();
		}
		boolean senderIsPlayer = sender instanceof Player;
		return new Message(instant, channelId, senderId, sender.getName(), senderType, senderIsPlayer, extraData, message);
	}

	// Normally called through a channel
	protected static Message createMessage(Channel channel, CommandSender sender, JsonObject extraData, String message, boolean markdown, Set<TransformationType> textTransformations) {
		MiniMessage.Builder minimessageBuilder = MiniMessage.builder()
		    .removeDefaultTransformations();

		if (markdown) {
			minimessageBuilder.markdown()
			    .markdownFlavor(DiscordFlavor.get());
		}

		for (TransformationType transform : textTransformations) {
			minimessageBuilder.transformation(transform);
		}

		Component messageComponent = minimessageBuilder.build().parse(message);

		return Message.createMessage(channel, sender, extraData, messageComponent);
	}

	// For when receiving remote messages
	protected static Message fromJson(JsonObject object) {
		Instant instant = Instant.ofEpochMilli(object.get("instant").getAsLong());;
		UUID channelId = UUID.fromString(object.get("channelId").getAsString());
		UUID senderId = null;
		if (object.get("senderId") != null) {
			senderId = UUID.fromString(object.get("senderId").getAsString());
		}
		String senderName = object.get("senderName").getAsString();
		NamespacedKey senderType = null;
		if (object.get("senderType") != null) {
			senderType = NamespacedKey.fromString(object.get("senderType").getAsString());
		}
		Boolean senderIsPlayer = object.get("senderIsPlayer").getAsBoolean();
		JsonObject extraData = null;
		if (object.get("extra") != null) {
			extraData = object.get("extra").getAsJsonObject();
		}
		Component message = GsonComponentSerializer.gson().deserializeFromTree(object.get("message"));

		return new Message(instant, channelId, senderId, senderName, senderType, senderIsPlayer, extraData, message);
	}

	protected JsonObject toJson() {
		JsonObject object = new JsonObject();

		object.addProperty("instant", mInstant.toEpochMilli());
		object.addProperty("channelId", mChannelId.toString());
		if (mSenderId != null) {
			object.addProperty("senderId", mSenderId.toString());
		}
		object.addProperty("senderName", mSenderName);
		if (mSenderType != null) {
			object.addProperty("senderType", mSenderType.toString());
		}
		object.addProperty("senderIsPlayer", mSenderIsPlayer);
		if (mExtraData != null) {
			object.add("extra", mExtraData);
		}
		object.add("message", GsonComponentSerializer.gson().serializeToTree(mMessage));

		return object;
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

	public UUID getSenderId() {
		return mSenderId;
	}

	public String getSenderName() {
		return mSenderName;
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

	public boolean isDeleted() {
		return mIsDeleted;
	}

	// Must be called from PlayerState to allow pausing messages.
	// Returns false if the channel is not loaded.
	protected boolean showMessage(CommandSender recipient) {
		if (mIsDeleted) {
			return true;
		}
		Channel channel = ChannelManager.getChannel(mChannelId);
		if (channel == null) {
			return false;
		}
		channel.showMessage(recipient, this);
		return true;
	}

	// This should be called by the manager to ensure chat is resent.
	protected void markDeleted() {
		mIsDeleted = true;
	}
}