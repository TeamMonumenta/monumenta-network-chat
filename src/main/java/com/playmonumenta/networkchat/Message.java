package com.playmonumenta.networkchat;

import java.time.Instant;
import java.util.UUID;

import org.bukkit.command.CommandSender;

public class Message {
	private final Instant mInstant;
	private final UUID mChannelId;
	private final String mSender;
	private final String mMessage;
	private boolean mIsDeleted = false;

	// Normally called through a channel
	protected Message(ChannelBase channel, CommandSender sender, String message) {
		mInstant = Instant.now();
		mChannelId = channel.getUniqueId();
		mSender = sender.getName();
		mMessage = message;
	}

	// For when receiving remote messages
	private Message(Instant instant, UUID channelId, String sender, String message) {
		mInstant = instant;
		mChannelId = channelId;
		mSender = sender;
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

	public String getSender() {
		return mSender;
	}

	public String getMessage() {
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
