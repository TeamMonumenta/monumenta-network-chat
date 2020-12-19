package com.playmonumenta.networkchat;

public class PlayerChatState {
	private ChatChannelBase mActiveChannel;

	public PlayerChatState() {
		// TODO Get default channel here
		mActiveChannel = null;
	}

	public ChatChannelBase getActiveChannel() {
		return mActiveChannel;
	}
}
