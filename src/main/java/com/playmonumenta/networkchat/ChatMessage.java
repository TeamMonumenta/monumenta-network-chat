package com.playmonumenta.networkchat;

import java.time.Instant;

public class ChatMessage {
	private final Instant mInstant;
	private final ChatChannelBase mChannel;
	private final String mSender;
	private final String mMessage;
	private boolean mIsDeleted = false;

	public ChatMessage(ChatChannelBase channel, String sender, String message) {
		mInstant = Instant.now();
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

	// This should be called by the manager to ensure chat is resent.
	protected void markDeleted() {
		mIsDeleted = true;
	}
}
