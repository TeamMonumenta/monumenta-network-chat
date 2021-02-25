package com.playmonumenta.networkchat;

import java.lang.NumberFormatException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.bukkit.entity.Player;

// TODO Track how many players are in a channel on this server/overall
public class PlayerChatState {
	// From vanilla client limits
	public static final int MAX_DISPLAYED_MESSAGES = 100;

	private Player mPlayer;
	private boolean mChatPaused;
	private UUID mActiveChannelId;

	// Channels not in these sets will use the default channel watch status.
	private Set<UUID> mWatchedChannelIds;
	private Set<UUID> mUnwatchedChannelIds;

	private List<ChatMessage> mSeenMessages;
	private List<ChatMessage> mUnseenMessages;

	public PlayerChatState(Player player) {
		mPlayer = player;
		mChatPaused = false;

		mWatchedChannelIds = new HashSet<UUID>();
		mUnwatchedChannelIds = new HashSet<UUID>();

		// TODO Get default channel here
		unsetActiveChannel();

		mSeenMessages = new ArrayList<ChatMessage>(MAX_DISPLAYED_MESSAGES);
		mUnseenMessages = new ArrayList<ChatMessage>(MAX_DISPLAYED_MESSAGES);
	}

	public static PlayerChatState fromJson(Player player, JsonObject obj) {
		PlayerChatState state = new PlayerChatState(player);

		// TODO for when message playback is implemented for transferring between servers.
		/*
		Instant lastLogin = null;
		JsonPrimitive lastLoginJson = obj.getAsJsonPrimitive("lastSaved");
		if (lastLoginJson != null) {
			try {
				lastLogin = new Instant.ofEpochMilli(lastLoginJson.getAsLong());
			} catch (NumberFormatException e) {
				;
			}
		}
		*/

		JsonPrimitive isPausedJson = obj.getAsJsonPrimitive("isPaused");
		if (isPausedJson != null) {
			state.mChatPaused = isPausedJson.getAsBoolean();
		}

		JsonPrimitive activeChannelJson = obj.getAsJsonPrimitive("activeChannel");
		if (activeChannelJson != null) {
			state.mActiveChannelId = UUID.fromString(activeChannelJson.getAsString());
		}

		JsonArray watchedChannelsJson = obj.getAsJsonArray("watchedChannels");
		if (watchedChannelsJson != null) {
			for (JsonElement channelIdElement : watchedChannelsJson) {
				try {
					state.mWatchedChannelIds.add(UUID.fromString(channelIdElement.getAsString()));
				} catch (Exception e) {
					;
				}
			}
		}

		JsonArray unwatchedChannelsJson = obj.getAsJsonArray("unwatchedChannels");
		if (unwatchedChannelsJson != null) {
			for (JsonElement channelIdElement : unwatchedChannelsJson) {
				try {
					state.mUnwatchedChannelIds.add(UUID.fromString(channelIdElement.getAsString()));
				} catch (Exception e) {
					;
				}
			}
		}

		return state;
	}

	public JsonObject toJson() {
		JsonArray watchedChannels = new JsonArray();
		for (UUID channelId : mWatchedChannelIds) {
			watchedChannels.add(channelId.toString());
		}

		JsonArray unwatchedChannels = new JsonArray();
		for (UUID channelId : mUnwatchedChannelIds) {
			unwatchedChannels.add(channelId.toString());
		}

		JsonObject result = new JsonObject();
		result.addProperty("lastSaved", Instant.now().toEpochMilli());
		result.addProperty("isPaused", mChatPaused);
		if (mActiveChannelId != null) {
			result.addProperty("activeChannel", mActiveChannelId.toString());
		}
		result.add("watchedChannels", watchedChannels);
		result.add("unwatchedChannels", unwatchedChannels);

		return result;
	}

	public void receiveMessage(ChatMessage message) {
		if (message.isDeleted()) {
			return;
		}
		UUID channelId = message.getChannelUniqueId();

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

	public boolean isPaused() {
		return mChatPaused;
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
		mActiveChannelId = channel.getUniqueId();
	}

	public void unsetActiveChannel() {
		// TODO Consider setting a default channel?
		mActiveChannelId = null;
	}

	public Set<UUID> getWatchedChannelIds() {
		return new HashSet<>(mWatchedChannelIds);
	}

	public Set<UUID> getUnwatchedChannelIds() {
		return new HashSet<>(mUnwatchedChannelIds);
	}

	public void joinChannel(ChatChannelBase channel) {
		UUID channelId = channel.getUniqueId();
		mWatchedChannelIds.add(channelId);
		mUnwatchedChannelIds.remove(channelId);
		if (mActiveChannelId == null) {
			mActiveChannelId = channelId;
		}
	}

	public void leaveChannel(ChatChannelBase channel) {
		UUID channelId = channel.getUniqueId();
		if (channelId == mActiveChannelId) {
			unsetActiveChannel();
		}
		mWatchedChannelIds.remove(channelId);
		mUnwatchedChannelIds.add(channelId);
	}

	// For channel deletion
	public void unregisterChannel(UUID channelId) {
		if (channelId == mActiveChannelId) {
			unsetActiveChannel();
		}
		mWatchedChannelIds.remove(channelId);
		mUnwatchedChannelIds.remove(channelId);
	}

	public UUID getActiveChannelId() {
		return mActiveChannelId;
	}

	public ChatChannelBase getActiveChannel() {
		return ChatManager.getChannel(mActiveChannelId);
	}

	public boolean isListening(ChatChannelBase channel) {
		UUID channelId = channel.getUniqueId();
		if (mUnwatchedChannelIds.contains(channelId)) {
			return false;
		} else if (mWatchedChannelIds.contains(channelId)) {
			return true;
		} else {
			// TODO Use channel's settings, NYI
			return true;
		}
	}

	public Set<ChatChannelBase> getMutedChannels() {
		Set<ChatChannelBase> channels = new HashSet<ChatChannelBase>();
		for (UUID channelId : mUnwatchedChannelIds) {
			channels.add(ChatManager.getChannel(channelId));
		}
		return channels;
	}
}
