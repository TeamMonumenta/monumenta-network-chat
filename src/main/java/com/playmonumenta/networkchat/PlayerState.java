package com.playmonumenta.networkchat;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.destroystokyo.paper.ClientOption;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkchat.utils.MessagingUtils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

// TODO Track how many players are in a channel on this server/overall
public class PlayerState {
	private final UUID mPlayerId;
	private boolean mChatPaused;
	private UUID mActiveChannelId;
	private ChannelSettings mDefaultChannelSettings = new ChannelSettings();
	private DefaultChannels mDefaultChannels = new DefaultChannels();
	private final Map<UUID, ChannelSettings> mChannelSettings;

	private final Map<UUID, UUID> mWhisperChannelsByRecipient;
	private final Map<UUID, UUID> mWhisperRecipientByChannels;
	private UUID mLastWhisperChannel;

	// Channels not in these maps will use the default channel watch status.
	private final Map<UUID, String> mWatchedChannelIds;
	private final Map<UUID, String> mUnwatchedChannelIds;

	private String mProfileMessage = "";

	public PlayerState(Player player) {
		mPlayerId = player.getUniqueId();
		mChatPaused = false;
		mChannelSettings = new HashMap<>();

		mWhisperChannelsByRecipient = new HashMap<>();
		mWhisperRecipientByChannels = new HashMap<>();
		mLastWhisperChannel = null;

		mWatchedChannelIds = new HashMap<>();
		mUnwatchedChannelIds = new HashMap<>();

		unsetActiveChannel();
	}

	public static PlayerState fromJson(Player player, JsonObject obj) {
		PlayerState state = new PlayerState(player);

		Instant now = Instant.now();
		Long nowMillis = now.toEpochMilli();
		Long lastLoginMillis = null;
		JsonPrimitive lastLoginJson = obj.getAsJsonPrimitive("lastSaved");
		if (lastLoginJson != null) {
			try {
				lastLoginMillis = lastLoginJson.getAsLong();
			} catch (NumberFormatException e) {
				NetworkChatPlugin.getInstance().getLogger().warning("Could not get lastSaved time for player " + player.getName());
			}

			if (lastLoginMillis != null) {
				long millisOffline = nowMillis - lastLoginMillis;
				NetworkChatPlugin.getInstance().getLogger().finer(player.getName() + " was offline for " + Double.toString(millisOffline / 1000.0) + " seconds.");
			}
		}

		JsonPrimitive isPausedJson = obj.getAsJsonPrimitive("isPaused");
		if (isPausedJson != null) {
			state.mChatPaused = isPausedJson.getAsBoolean();
		}

		JsonPrimitive activeChannelJson = obj.getAsJsonPrimitive("activeChannel");
		if (activeChannelJson != null) {
			state.mActiveChannelId = UUID.fromString(activeChannelJson.getAsString());
		}

		JsonObject whisperChannelsJson = obj.getAsJsonObject("whisperChannels");
		if (whisperChannelsJson != null) {
			for (Map.Entry<String, JsonElement> whisperChannelEntry : whisperChannelsJson.entrySet()) {
				String recipientId = whisperChannelEntry.getKey();
				JsonElement channelIdJson = whisperChannelEntry.getValue();
				try {
					UUID recipientUuid = UUID.fromString(recipientId);
					UUID channelUuid = UUID.fromString(channelIdJson.getAsString());
					state.mWhisperChannelsByRecipient.put(recipientUuid, channelUuid);
					state.mWhisperRecipientByChannels.put(channelUuid, recipientUuid);
				} catch (Exception e) {
					NetworkChatPlugin.getInstance().getLogger().warning("Catch an exception while converting " + player.getName() + "'s whisperChannels to object. Reason: " + e.getMessage());
				}
			}
		}

		try {
			JsonPrimitive lastWhisperChannel = obj.getAsJsonPrimitive("lastWhisperChannel");
			state.mLastWhisperChannel = UUID.fromString(lastWhisperChannel.getAsString());
		} catch (Exception e) {
			state.mLastWhisperChannel = null;
		}

		JsonObject watchedChannelsJson = obj.getAsJsonObject("watchedChannels");
		if (watchedChannelsJson != null) {
			for (Map.Entry<String, JsonElement> channelIdEntry : watchedChannelsJson.entrySet()) {
				String channelId = channelIdEntry.getKey();
				JsonElement lastKnownChannelName = channelIdEntry.getValue();
				try {
					state.mWatchedChannelIds.put(UUID.fromString(channelId), lastKnownChannelName.getAsString());
				} catch (Exception e) {
					NetworkChatPlugin.getInstance().getLogger().warning("Catch an exception while converting " + player.getName() + "'s watchedChannels to object. Reason: " + e.getMessage());
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
					NetworkChatPlugin.getInstance().getLogger().warning("Catch an exception while converting " + player.getName() + "'s unwatchetChannels to object. Reason: " + e.getMessage());
				}
			}
		}

		JsonObject defaultChannelSettingsJson = obj.getAsJsonObject("defaultChannelSettings");
		if (defaultChannelSettingsJson != null) {
			state.mDefaultChannelSettings = ChannelSettings.fromJson(defaultChannelSettingsJson);
		}

		JsonObject defaultChannelsJson = obj.getAsJsonObject("defaultChannels");
		if (defaultChannelsJson != null) {
			state.mDefaultChannels = DefaultChannels.fromJson(defaultChannelsJson);
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
					NetworkChatPlugin.getInstance().getLogger().warning("Catch an exception while converting " + player.getName() + "'s channelSettings to object. Reason: " + e.getMessage());
				}
			}
		}

		JsonPrimitive profileMessageJson = obj.getAsJsonPrimitive("profileMessage");
		if (profileMessageJson != null && profileMessageJson.isString()) {
			state.mProfileMessage = profileMessageJson.getAsString();
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

		JsonObject whisperChannels = new JsonObject();
		for (Map.Entry<UUID, UUID> channelEntry : mWhisperChannelsByRecipient.entrySet()) {
			String recipient = channelEntry.getKey().toString();
			String channelId = channelEntry.getValue().toString();
			whisperChannels.addProperty(recipient, channelId);
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
		if (mProfileMessage != null && !mProfileMessage.isEmpty()) {
			result.addProperty("profileMessage", mProfileMessage);
		}
		result.add("whisperChannels", whisperChannels);
		if (mLastWhisperChannel != null) {
			result.addProperty("lastWhisperChannel", mLastWhisperChannel.toString());
		}
		result.add("watchedChannels", watchedChannels);
		result.add("unwatchedChannels", unwatchedChannels);
		result.add("defaultChannelSettings", mDefaultChannelSettings.toJson());
		result.add("defaultChannels", mDefaultChannels.toJson());
		result.add("channelSettings", allChannelSettings);

		return result;
	}

	public UUID getPlayerUniqueId() {
		return mPlayerId;
	}

	public Player getPlayer() {
		return Bukkit.getPlayer(mPlayerId);
	}

	public PlayerChatHistory getPlayerChatHistory() {
		return PlayerStateManager.getPlayerChatHistory(mPlayerId);
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

	public DefaultChannels defaultChannels() {
		return mDefaultChannels;
	}

	public void receiveMessage(Message message) {
		getPlayerChatHistory().receiveMessage(message);
	}

	public void receiveExternalMessage(Message message) {
		getPlayerChatHistory().receiveExternalMessage(message);
	}

	// Re-show chat with deleted messages removed, even while paused.
	public void refreshChat() {
		getPlayerChatHistory().refreshChat();
	}

	// For use from PlayerChatHistory
	protected void setPauseState(boolean isPaused) {
		mChatPaused = isPaused;
	}

	public boolean isPaused() {
		return mChatPaused;
	}

	public void pauseChat() {
		mChatPaused = true;
	}

	public void unpauseChat() {
		getPlayerChatHistory().unpauseChat();
	}

	public void setActiveChannel(Channel channel) {
		joinChannel(channel);
		mActiveChannelId = channel.getUniqueId();
	}

	public void unsetActiveChannel() {
		mActiveChannelId = null;
	}

	public ChannelWhisper getWhisperChannel(UUID recipientUuid) {
		UUID channelId = mWhisperChannelsByRecipient.get(recipientUuid);
		if (channelId == null) {
			return null;
		}
		Channel channel = ChannelManager.getChannel(channelId);
		if (!(channel instanceof ChannelWhisper)) {
			return null; // Odds are channel was null, otherwise something's very improbably wrong.
		}
		return (ChannelWhisper) channel;
	}

	public void setWhisperChannel(UUID recipientUuid, ChannelWhisper channel) {
		UUID channelUuid = channel.getUniqueId();
		UUID oldChannelUuid = mWhisperChannelsByRecipient.get(recipientUuid);
		if (oldChannelUuid != null) {
			mWhisperRecipientByChannels.remove(oldChannelUuid);
		}
		mWhisperChannelsByRecipient.put(recipientUuid, channelUuid);
		mWhisperRecipientByChannels.put(channelUuid, recipientUuid);
		mLastWhisperChannel = channelUuid;
	}

	public Set<UUID> getWatchedChannelIds() {
		return new HashSet<>(mWatchedChannelIds.keySet());
	}

	public Set<UUID> getUnwatchedChannelIds() {
		return new HashSet<>(mUnwatchedChannelIds.keySet());
	}

	public Set<UUID> getWhisperChannelIds() {
		return new HashSet<>(mWhisperRecipientByChannels.keySet());
	}

	public ChannelWhisper getLastWhisperChannel() {
		Channel channel = ChannelManager.getChannel(mLastWhisperChannel);
		if (!(channel instanceof ChannelWhisper)) {
			return null;
		}
		return (ChannelWhisper) channel;
	}

	public boolean hasSeenChannelId(UUID channelId) {
		return mWatchedChannelIds.containsKey(channelId)
		    || mUnwatchedChannelIds.containsKey(channelId);
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
		channelUpdated(channelId, null);
		if (channelId.equals(mActiveChannelId)) {
			unsetActiveChannel();
		}
		mWatchedChannelIds.remove(channelId);
		mUnwatchedChannelIds.remove(channelId);
		UUID whisperRecipientUuid = mWhisperRecipientByChannels.get(channelId);
		if (whisperRecipientUuid != null) {
			mWhisperRecipientByChannels.remove(channelId);
			mWhisperChannelsByRecipient.remove(whisperRecipientUuid);
		}
		if (channelId.equals(mLastWhisperChannel)) {
			mLastWhisperChannel = null;
		}
		mChannelSettings.remove(channelId);
		mDefaultChannels.unsetChannel(channelId);
	}

	public Channel getDefaultChannel(String channelType) {
		Channel channel = mDefaultChannels.getDefaultChannel(channelType);
		if (channel == null) {
			channel = ChannelManager.getDefaultChannel(channelType);
		}
		return channel;
	}

	public Channel getActiveChannel() {
		if (mActiveChannelId != null) {
			return ChannelManager.getChannel(mActiveChannelId);
		}
		return ChannelManager.getDefaultChannel();
	}

	public boolean isListening(Channel channel) {
		switch (getPlayer().getClientOption(ClientOption.CHAT_VISIBILITY)) {
		case HIDDEN:
			return false;
		case SYSTEM:
			if (!(channel instanceof ChannelAnnouncement)) {
				return false;
			}
			break;
		default:
		}

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

	public void playMessageSound(Message message) {
		boolean shouldPlaySound = false;

		Channel channel = message.getChannel();
		Player player = getPlayer();
		String plainMessage = message.getPlainMessage();
		if (plainMessage.contains("@" + player.getName())) {
			shouldPlaySound = true;
		} else if (plainMessage.contains("@everyone")) {
			shouldPlaySound = true;
		}
		if (shouldPlaySound) {
			player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.1f, 0.5f);
			return;
		}

		UUID channelId = channel.getUniqueId();
		ChannelSettings channelSettings = mChannelSettings.get(channelId);
		if (channelSettings != null && channelSettings.messagesPlaySound() != null) {
			shouldPlaySound = channelSettings.messagesPlaySound();
		} else if (channel.channelSettings() != null && channel.channelSettings().messagesPlaySound() != null) {
			shouldPlaySound = channel.channelSettings().messagesPlaySound();
		} else if (mDefaultChannelSettings.messagesPlaySound() != null) {
			shouldPlaySound = mDefaultChannelSettings.messagesPlaySound();
		} else if (channel instanceof ChannelWhisper) {
			shouldPlaySound = true;
		}

		if (shouldPlaySound) {
			if (channelSettings != null && !channelSettings.soundEmpty()) {
				channelSettings.playSounds(player);
			} else if (channel.channelSettings() != null && !channel.channelSettings().soundEmpty()) {
				channel.channelSettings().playSounds(player);
			} else if (!mDefaultChannelSettings.soundEmpty()) {
				mDefaultChannelSettings.playSounds(player);
			}

		}
	}

	public Set<Channel> getMutedChannels() {
		Set<Channel> channels = new HashSet<Channel>();
		for (UUID channelId : mUnwatchedChannelIds.keySet()) {
			channels.add(ChannelManager.getChannel(channelId));
		}
		return channels;
	}

	/**
	 * Sends a message to the player if the channel was renamed or deleted.
	 *
	 * @param channelId ID of the updated channel
	 * @param channel Channel that has changed. null if deleted.
	 */
	protected void channelUpdated(UUID channelId, @Nullable Channel channel) {
		String newChannelName = null;
		if (channel != null) {
			newChannelName = channel.getName();
		}
		String lastKnownName = mWatchedChannelIds.get(channelId);
		if (lastKnownName != null) {
			mWatchedChannelIds.put(channelId, newChannelName);
		}
		boolean showAlert = true;
		if (lastKnownName == null) {
			lastKnownName = mUnwatchedChannelIds.get(channelId);
			if (lastKnownName != null) {
				mUnwatchedChannelIds.put(channelId, newChannelName);
			}
		}
		UUID whisperRecipientUuid;
		if (lastKnownName == null) {
			whisperRecipientUuid = mWhisperRecipientByChannels.get(channelId);
			if (whisperRecipientUuid != null) {
				lastKnownName = "whisper chat with...someone?";
				showAlert = false;
			}
		}
		if (lastKnownName == null) {
			return;
		}
		if (channel == null) {
			// Channel was deleted
			unregisterChannel(channelId);
			// TODO Group deleted channel messages together.
			if (showAlert) {
				getPlayer().sendMessage(Component.text("The channel you knew as " + lastKnownName + " is no longer available.", NamedTextColor.RED));
			}
		} else {
			if (showAlert && !newChannelName.equals(lastKnownName)) {
				getPlayer().sendMessage(Component.text("The channel you knew as " + lastKnownName + " is now known as " + newChannelName + ".", NamedTextColor.GRAY));
			}
		}
	}

	public Component profileMessageComponent() {
		Player player = getPlayer();
		return MessagingUtils.getAllowedMiniMessage(player).parse(mProfileMessage);
	}

	public String profileMessage() {
		return mProfileMessage;
	}

	public void profileMessage(String profileMessage) {
		if (profileMessage == null) {
			mProfileMessage = "";
		} else {
			mProfileMessage = profileMessage;
		}
		RemotePlayerManager.refreshLocalPlayer(getPlayer());
	}
}
