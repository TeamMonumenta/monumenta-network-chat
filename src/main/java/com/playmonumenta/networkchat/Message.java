package com.playmonumenta.networkchat;

import java.time.Instant;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.transformation.TransformationType;
import net.kyori.adventure.text.minimessage.markdown.DiscordFlavor;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Message {
	private final Instant mInstant;
	private final UUID mChannelId;
	private final UUID mSenderId;
	private final String mSenderName;
	private final Component mMessage;
	private boolean mIsDeleted = false;

	// Normally called through a channel
	protected Message(ChannelBase channel, CommandSender sender, Component message) {
		mInstant = Instant.now();
		mChannelId = channel.getUniqueId();
		if (sender instanceof Player) {
			mSenderId = ((Player) sender).getUniqueId();
		} else {
			mSenderId = null;
		}
		mSenderName = sender.getName();
		mMessage = message;
	}

	// Normally called through a channel
	protected Message(ChannelBase channel, CommandSender sender, String message, boolean allowDecoration, boolean allowColor) {
		mInstant = Instant.now();
		mChannelId = channel.getUniqueId();
		if (sender instanceof Player) {
			mSenderId = ((Player) sender).getUniqueId();
		} else {
			mSenderId = null;
		}
		mSenderName = sender.getName();

		MiniMessage.Builder minimessageBuilder = MiniMessage.builder()
		    .removeDefaultTransformations();

		if (allowColor) {
			minimessageBuilder.transformation(TransformationType.COLOR);
		}

		if (allowDecoration) {
			minimessageBuilder.transformation(TransformationType.DECORATION)
			    .markdown()
			    .markdownFlavor(DiscordFlavor.get());
		}

		mMessage = minimessageBuilder.build().parse(message);
	}

	// For when receiving remote messages
	private Message(Instant instant, UUID channelId, UUID senderId, String senderName, Component message) {
		mInstant = instant;
		mChannelId = channelId;
		mSenderId = senderId;
		mSenderName = senderName;
		mMessage = message;
	}

	public Instant getInstant() {
		return mInstant;
	}

	public UUID getChannelUniqueId() {
		return mChannelId;
	}

	public ChannelBase getChannel() {
		return ChannelManager.getChannel(mChannelId);
	}

	public UUID getSenderId() {
		return mSenderId;
	}

	public String getSenderName() {
		return mSenderName;
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
		ChannelBase channel = ChannelManager.getChannel(mChannelId);
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
