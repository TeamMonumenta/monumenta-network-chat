package com.playmonumenta.networkchat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;

public class PlayerChatState {
	// From vanilla client limits
	public static final int MAX_DISPLAYED_MESSAGES = 100;

	private Player mPlayer;
	private boolean mChatPaused;
	private ChatChannelBase mActiveChannel;

	// Channels not in these sets will use the default channel watch status.
	private Set<ChatChannelBase> mWatchedChannels;
	private Set<ChatChannelBase> mUnwatchedChannels;

	private List<ChatMessage> mSeenMessages;
	private List<ChatMessage> mUnseenMessages;

	public PlayerChatState(Player player) {
		mPlayer = player;
		mChatPaused = false;

		mWatchedChannels = new HashSet<ChatChannelBase>();
		mUnwatchedChannels = new HashSet<ChatChannelBase>();

		// TODO Get default channel here
		unsetActiveChannel();

		mSeenMessages = new ArrayList<ChatMessage>(MAX_DISPLAYED_MESSAGES);
		mUnseenMessages = new ArrayList<ChatMessage>(MAX_DISPLAYED_MESSAGES);
	}

	public void receiveMessage(ChatMessage message) {
		if (message.isDeleted()) {
			return;
		}

		if (mChatPaused) {
			if (mUnseenMessages.size() >= MAX_DISPLAYED_MESSAGES) {
				mUnseenMessages.remove(0);
			}
			mUnseenMessages.add(message);
		} else {
			if (mSeenMessages.size() >= MAX_DISPLAYED_MESSAGES) {
				mSeenMessages.remove(0);
			}
			message.showMessage(mPlayer);
			mSeenMessages.add(message);
		}
	}

	// Re-show chat with deleted messages removed, even while paused.
	public void refreshChat() {
		int blank_messages = MAX_DISPLAYED_MESSAGES - mSeenMessages.size();
		for (int i = 0; i < blank_messages; ++i) {
			mPlayer.sendMessage("");
		}

		for (ChatMessage message : mSeenMessages) {
			message.showMessage(mPlayer);
		}
	}

	public void pauseChat() {
		mChatPaused = true;
	}

	public void unpauseChat() {
		// Delete old seen messages
		if (mUnseenMessages.size() + mSeenMessages.size() > MAX_DISPLAYED_MESSAGES) {
			int newSeenStartIndex = mUnseenMessages.size() + mSeenMessages.size() - MAX_DISPLAYED_MESSAGES;
			mSeenMessages = mSeenMessages.subList(newSeenStartIndex, MAX_DISPLAYED_MESSAGES);
		}

		// Show all unseen messages
		for (ChatMessage message : mUnseenMessages) {
			message.showMessage(mPlayer);
		}
		mSeenMessages.addAll(mUnseenMessages);
		mUnseenMessages.clear();

		mChatPaused = false;
	}

	public void setActiveChannel(ChatChannelBase channel) {
		joinChannel(channel);
		mActiveChannel = channel;
	}

	public void unsetActiveChannel() {
		// TODO Consider setting a default channel?
		mActiveChannel = null;
	}

	public void joinChannel(ChatChannelBase channel) {
		mWatchedChannels.add(channel);
		mUnwatchedChannels.remove(channel);
		if (mActiveChannel == null) {
			mActiveChannel = channel;
		}
	}

	public void leaveChannel(ChatChannelBase channel) {
		if (channel == mActiveChannel) {
			unsetActiveChannel();
		}
		mWatchedChannels.remove(channel);
		mUnwatchedChannels.add(channel);
	}

	public ChatChannelBase getActiveChannel() {
		return mActiveChannel;
	}

	public boolean isListening(ChatChannelBase channel) {
		return !mUnwatchedChannels.contains(channel);
	}

	public Set<ChatChannelBase> getMutedChannels() {
		return new HashSet<ChatChannelBase>(mUnwatchedChannels);
	}
}
