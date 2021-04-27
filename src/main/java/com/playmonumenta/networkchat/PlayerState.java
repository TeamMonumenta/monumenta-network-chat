package com.playmonumenta.networkchat;

import java.lang.NumberFormatException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

// TODO Track how many players are in a channel on this server/overall
public class PlayerState {
	// From vanilla client limits
	public static final int MAX_DISPLAYED_MESSAGES = 100;

	private UUID mPlayerId;
	private boolean mChatPaused;
	private UUID mActiveChannelId;
	private ChannelSettings mDefaultChannelSettings = new ChannelSettings();
	private Map<UUID, ChannelSettings> mChannelSettings;

	// Channels not in these maps will use the default channel watch status.
	private Map<UUID, String> mWatchedChannelIds;
	private Map<UUID, String> mUnwatchedChannelIds;

	private List<Message> mSeenMessages;
	private List<Message> mUnseenMessages;

	public PlayerState(Player player) {
		mPlayerId = player.getUniqueId();
		mChatPaused = false;
		mChannelSettings = new HashMap<>();

		mWatchedChannelIds = new HashMap<>();
		mUnwatchedChannelIds = new HashMap<>();

		// TODO Get default channel here
		unsetActiveChannel();

		mSeenMessages = new ArrayList<Message>(MAX_DISPLAYED_MESSAGES);
		mUnseenMessages = new ArrayList<Message>(MAX_DISPLAYED_MESSAGES);
	}

	public static PlayerState fromJson(Player player, JsonObject obj) {
		PlayerState state = new PlayerState(player);

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

		JsonObject watchedChannelsJson = obj.getAsJsonObject("watchedChannels");
		if (watchedChannelsJson != null) {
			for (Map.Entry<String, JsonElement> channelIdEntry : watchedChannelsJson.entrySet()) {
				String channelId = channelIdEntry.getKey();
				JsonElement lastKnownChannelName = channelIdEntry.getValue();
				try {
					state.mWatchedChannelIds.put(UUID.fromString(channelId), lastKnownChannelName.getAsString());
				} catch (Exception e) {
					// TODO Log the error
					continue;
				}
			}
		}

		JsonObject unwatchedChannelsJson = obj.getAsJsonObject("unwatchedChannels");
		if (unwatchedChannelsJson != null) {
			for (Map.Entry<String, JsonElement> channelIdEntry : unwatchedChannelsJson.entrySet()) {
				String channelId = channelIdEntry.getKey();
				JsonElement lastKnownChannelName = channelIdEntry.getValue();
				try {
					state.mUnwatchedChannelIds.put(UUID.fromString(channelId), lastKnownChannelName.getAsString());
				} catch (Exception e) {
					// TODO Log the error
					continue;
				}
			}
		}

		JsonObject defaultChannelSettingsJson = obj.getAsJsonObject("defaultChannelSettings");
		if (defaultChannelSettingsJson != null) {
			state.mDefaultChannelSettings = ChannelSettings.fromJson(defaultChannelSettingsJson);
		}

		JsonObject allChannelSettingsJson = obj.getAsJsonObject("channelSettings");
		if (allChannelSettingsJson != null) {
			for (Map.Entry<String, JsonElement> channelSettingEntry : allChannelSettingsJson.entrySet()) {
				String channelId = channelSettingEntry.getKey();
				JsonElement channelSettingJson = channelSettingEntry.getValue();
				try {
					ChannelSettings channelSettings = ChannelSettings.fromJson(channelSettingJson.getAsJsonObject());
					state.mChannelSettings.put(UUID.fromString(channelId), channelSettings);
				} catch (Exception e) {
					// TODO Log the error
					continue;
				}
			}
		}

		return state;
	}

	public JsonObject toJson() {
		JsonObject watchedChannels = new JsonObject();
		for (Map.Entry<UUID, String> channelEntry : mWatchedChannelIds.entrySet()) {
			UUID channelId = channelEntry.getKey();
			String channelName = channelEntry.getValue();
			watchedChannels.addProperty(channelId.toString(), channelName);
		}

		JsonObject unwatchedChannels = new JsonObject();
		for (Map.Entry<UUID, String> channelEntry : mUnwatchedChannelIds.entrySet()) {
			UUID channelId = channelEntry.getKey();
			String channelName = channelEntry.getValue();
			unwatchedChannels.addProperty(channelId.toString(), channelName);
		}

		JsonObject allChannelSettings = new JsonObject();
		for (Map.Entry<UUID, ChannelSettings> channelSettingsEntry : mChannelSettings.entrySet()) {
			UUID channelId = channelSettingsEntry.getKey();
			ChannelSettings channelSettings = channelSettingsEntry.getValue();
			if (!channelSettings.isDefault()) {
				allChannelSettings.add(channelId.toString(), channelSettings.toJson());
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("lastSaved", Instant.now().toEpochMilli());
		result.addProperty("isPaused", mChatPaused);
		if (mActiveChannelId != null) {
			result.addProperty("activeChannel", mActiveChannelId.toString());
		}
		result.add("watchedChannels", watchedChannels);
		result.add("unwatchedChannels", unwatchedChannels);
		result.add("defaultChannelSettings", mDefaultChannelSettings.toJson());
		result.add("channelSettings", allChannelSettings);

		return result;
	}

	public Player getPlayer() {
		return Bukkit.getPlayer(mPlayerId);
	}

	public ChannelSettings channelSettings() {
		return mDefaultChannelSettings;
	}

	public ChannelSettings channelSettings(Channel channel) {
		UUID channelId = channel.getUniqueId();
		ChannelSettings channelSettings = mChannelSettings.get(channelId);
		if (channelSettings == null) {
			channelSettings = new ChannelSettings();
			mChannelSettings.put(channelId, channelSettings);
		}
		return channelSettings;
	}

	public void receiveMessage(Message message) {
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
			message.showMessage(getPlayer());
			mSeenMessages.add(message);
		}
	}

	// Re-show chat with deleted messages removed, even while paused.
	public void refreshChat() {
		int blank_messages = MAX_DISPLAYED_MESSAGES - mSeenMessages.size();
		for (int i = 0; i < blank_messages; ++i) {
			getPlayer().sendMessage(Component.empty());
		}

		for (Message message : mSeenMessages) {
			message.showMessage(getPlayer());
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
		for (Message message : mUnseenMessages) {
			message.showMessage(getPlayer());
		}
		mSeenMessages.addAll(mUnseenMessages);
		mUnseenMessages.clear();

		mChatPaused = false;
	}

	public void setActiveChannel(Channel channel) {
		joinChannel(channel);
		mActiveChannelId = channel.getUniqueId();
	}

	public void unsetActiveChannel() {
		mActiveChannelId = null;
	}

	public Set<UUID> getWatchedChannelIds() {
		return new HashSet<>(mWatchedChannelIds.keySet());
	}

	public Set<UUID> getUnwatchedChannelIds() {
		return new HashSet<>(mUnwatchedChannelIds.keySet());
	}

	public boolean isWatchingChannelId(UUID channelId) {
		return mWatchedChannelIds.containsKey(channelId);
	}

	public void joinChannel(Channel channel) {
		UUID channelId = channel.getUniqueId();
		String channelName = channel.getName();
		mWatchedChannelIds.put(channelId, channelName);
		mUnwatchedChannelIds.remove(channelId);
	}

	public void leaveChannel(Channel channel) {
		UUID channelId = channel.getUniqueId();
		String channelName = channel.getName();
		if (channelId == mActiveChannelId) {
			unsetActiveChannel();
		}
		mWatchedChannelIds.remove(channelId);
		mUnwatchedChannelIds.put(channelId, channelName);
	}

	// For channel deletion
	public void unregisterChannel(UUID channelId) {
		if (channelId == mActiveChannelId) {
			unsetActiveChannel();
		}
		mWatchedChannelIds.remove(channelId);
		mUnwatchedChannelIds.remove(channelId);
		mChannelSettings.remove(channelId);
	}

	public UUID getActiveChannelId() {
		if (mActiveChannelId != null) {
			return mActiveChannelId;
		}
		return ChannelManager.getDefaultChannel().getUniqueId();
	}

	public Channel getActiveChannel() {
		if (mActiveChannelId != null) {
			return ChannelManager.getChannel(mActiveChannelId);
		}
		return ChannelManager.getDefaultChannel();
	}

	public boolean isListening(Channel channel) {
		UUID channelId = channel.getUniqueId();

		ChannelSettings channelSettings = mChannelSettings.get(channelId);
		if (channelSettings != null && channelSettings.isListening() != null) {
			return channelSettings.isListening();
		}

		if (mDefaultChannelSettings.isListening() != null) {
			return mDefaultChannelSettings.isListening();
		}

		if (mUnwatchedChannelIds.containsKey(channelId)) {
			return false;
		} else if (mWatchedChannelIds.containsKey(channelId)) {
			return true;
		}

		return true;
	}

	public void playMessageSound(Channel channel) {
		boolean shouldPlaySound = false;

		UUID channelId = channel.getUniqueId();
		ChannelSettings channelSettings = mChannelSettings.get(channelId);
		if (channelSettings != null && channelSettings.messagesPlaySound() != null) {
			shouldPlaySound = channelSettings.messagesPlaySound();
		} else if (channel.channelSettings() != null && channel.channelSettings().messagesPlaySound() != null) {
			shouldPlaySound = channel.channelSettings().messagesPlaySound();
		} else if (mDefaultChannelSettings.messagesPlaySound() != null) {
			shouldPlaySound = mDefaultChannelSettings.messagesPlaySound();
		}
		// TODO Player default

		if (shouldPlaySound) {
			Player player = getPlayer();
			// TODO Customize sound
			player.playSound​(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 0.5f);
		}
	}

	public Set<Channel> getMutedChannels() {
		Set<Channel> channels = new HashSet<Channel>();
		for (UUID channelId : mUnwatchedChannelIds.keySet()) {
			channels.add(ChannelManager.getChannel(channelId));
		}
		return channels;
	}

	protected void channelLoaded(UUID channelId) {
		Channel loadedChannel = ChannelManager.getChannel(channelId);
		String lastKnownName = mWatchedChannelIds.get(channelId);
		if (lastKnownName == null) {
			lastKnownName = mUnwatchedChannelIds.get(channelId);
		}
		if (lastKnownName == null) {
			lastKnownName = "...something?...";
		}
		if (loadedChannel == null) {
			// Channel was deleted
			unregisterChannel(channelId);
			// TODO Group deleted channel messages together.
			getPlayer().sendMessage(Component.text("The channel you knew as " + lastKnownName + " is no longer available.", NamedTextColor.RED));
		} else {
			String newName = loadedChannel.getName();
			if (!newName.equals(lastKnownName)) {
				getPlayer().sendMessage(Component.text("The channel you knew as " + lastKnownName + " is now known as " + newName + ".", NamedTextColor.GRAY));
			}
		}
	}
}
