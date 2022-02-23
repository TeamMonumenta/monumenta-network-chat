package com.playmonumenta.networkchat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

// Container for chat messages to be replayed if a message is deleted.
// This is discarded if the player has been offline long enough.
public class PlayerChatHistory {
	protected static final String NETWORK_CHAT_PLAYER_CHAT_HISTORY = "com.playmonumenta.networkchat.player_chat_history";

	// From vanilla client limits
	public static final int MAX_DISPLAYED_MESSAGES = 100;
	protected static final long MAX_OFFLINE_HISTORY_SECONDS = 10;

	private final UUID mPlayerId;
	private boolean mIsReplayingChat = false;
	private boolean mIsDisplayingMessage = false;
	private List<Message> mSeenMessages = new ArrayList<Message>(MAX_DISPLAYED_MESSAGES);
	private List<Message> mUnseenMessages = new ArrayList<Message>(MAX_DISPLAYED_MESSAGES);

	public PlayerChatHistory(UUID playerId) {
		mPlayerId = playerId;
	}

	public void updateFromJson(JsonObject obj) {
		JsonArray seenMessagesJson = obj.getAsJsonArray("seenMessages");
		if (seenMessagesJson != null) {
			mSeenMessages = new ArrayList<Message>(MAX_DISPLAYED_MESSAGES);
			for (JsonElement messageJson : seenMessagesJson) {
				if (messageJson instanceof JsonObject) {
					Message message = Message.fromJson((JsonObject) messageJson);
					mSeenMessages.add(message);
				}
			}
		}

		JsonArray unseenMessagesJson = obj.getAsJsonArray("unseenMessages");
		if (unseenMessagesJson != null) {
			mUnseenMessages = new ArrayList<Message>(MAX_DISPLAYED_MESSAGES);
			for (JsonElement messageJson : unseenMessagesJson) {
				if (messageJson instanceof JsonObject) {
					Message message = Message.fromJson((JsonObject) messageJson);
					mUnseenMessages.add(message);
				}
			}
		}
	}

	public JsonObject toJson() {
		List<Message> seenMessagesCopy = new ArrayList<Message>(mSeenMessages);
		JsonArray seenMessages = new JsonArray();
		for (Message message : seenMessagesCopy) {
			seenMessages.add(message.toJson());
		}
		List<Message> unseenMessagesCopy = new ArrayList<Message>(mUnseenMessages);
		JsonArray unseenMessages = new JsonArray();
		for (Message message : unseenMessagesCopy) {
			unseenMessages.add(message.toJson());
		}

		JsonObject result = new JsonObject();
		result.addProperty("playerId", mPlayerId.toString());
		result.add("seenMessages", seenMessages);
		result.add("unseenMessages", unseenMessages);

		return result;
	}

	public Player getPlayer() {
		return Bukkit.getPlayer(mPlayerId);
	}

	public PlayerState getPlayerState() {
		return PlayerStateManager.getPlayerState(mPlayerId);
	}

	public void receiveMessage(Message message) {
		if (message.isDeleted()) {
			return;
		}

		if (getPlayerState().isPaused() || mIsReplayingChat) {
			if (mUnseenMessages.size() >= MAX_DISPLAYED_MESSAGES) {
				mUnseenMessages.remove(0);
			}
			mUnseenMessages.add(message);
		} else {
			if (mSeenMessages.size() >= MAX_DISPLAYED_MESSAGES) {
				mSeenMessages.remove(0);
			}
			mIsDisplayingMessage = true;
			message.showMessage(getPlayer());
			mIsDisplayingMessage = false;
			mSeenMessages.add(message);
		}
	}

	public void receiveExternalMessage(Message message) {
		if (mIsReplayingChat || mIsDisplayingMessage) {
			return;
		}
		if (mSeenMessages.size() >= MAX_DISPLAYED_MESSAGES) {
			mSeenMessages.remove(0);
		}
		mSeenMessages.add(message);
	}

	// Re-show chat with deleted messages removed, even while paused.
	public void refreshChat() {
		mIsReplayingChat = true;
		mSeenMessages.removeIf(Message::isDeleted);

		int messageCount = MAX_DISPLAYED_MESSAGES - mSeenMessages.size();
		Component emptyMessage = Component.empty();
		for (int i = 0; i < messageCount; ++i) {
			getPlayer().sendMessage(emptyMessage);
		}

		mIsDisplayingMessage = true;
		for (Message message : mSeenMessages) {
			message.showMessage(getPlayer());
		}
		mIsDisplayingMessage = false;
		mIsReplayingChat = false;
		if (!getPlayerState().isPaused()) {
			showUnseen();
		}
	}

	public boolean isPaused() {
		return getPlayerState().isPaused();
	}

	public void pauseChat() {
		getPlayerState().setPauseState(true);
	}

	public void unpauseChat() {
		// Delete old seen messages
		if (mUnseenMessages.size() + mSeenMessages.size() > MAX_DISPLAYED_MESSAGES) {
			int newSeenStartIndex = mUnseenMessages.size() + mSeenMessages.size() - MAX_DISPLAYED_MESSAGES;
			mSeenMessages = mSeenMessages.subList(newSeenStartIndex, MAX_DISPLAYED_MESSAGES);
		}
		showUnseen();
	}

	private void showUnseen() {
		mIsDisplayingMessage = true;
		Player player = getPlayer();
		for (Message message : mUnseenMessages) {
			message.showMessage(player);
		}
		mSeenMessages.addAll(mUnseenMessages);
		mUnseenMessages.clear();
		mIsDisplayingMessage = false;

		getPlayerState().setPauseState(false);
	}
}
