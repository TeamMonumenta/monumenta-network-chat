package com.playmonumenta.networkchat;

import java.time.Instant;

import org.bukkit.command.CommandSender;

public class ChatMessage {
	private final Instant mInstant;
	private final ChatChannelBase mChannel;
	private final String mSender;
	private final String mMessage;
	private boolean mIsDeleted = false;

	// Normally called through a channel
	protected ChatMessage(ChatChannelBase channel, CommandSender sender, String message) {
		mInstant = Instant.now();
		mChannel = channel;
		mSender = sender.getName();
		mMessage = message;
	}

	// For when receiving remote messages
	private ChatMessage(Instant instant, ChatChannelBase channel, String sender, String message) {
		mInstant = instant;
		mChannel = channel;
		mSender = sender;
		mMessage = message;
	}

	public Instant getInstant() {
		return mInstant;
	}

	public ChatChannelBase getChannel() {
		return mChannel;
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

	// Must be called from PlayerChatState to allow pausing messages.
	protected void showMessage(CommandSender recipient) {
		mChannel.showMessage(recipient, this);
	}

	// This should be called by the manager to ensure chat is resent.
	protected void markDeleted() {
		mIsDeleted = true;
	}
}
