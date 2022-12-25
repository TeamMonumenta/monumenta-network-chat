package com.playmonumenta.networkchat;

import com.destroystokyo.paper.ClientOption;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

// TODO Track how many players are in a channel on this server/overall
public class PlayerState {
	private final UUID mPlayerId;
	private boolean mChatPaused;
	private @Nullable UUID mActiveChannelId;
	private ChannelSettings mDefaultChannelSettings = new ChannelSettings();
	private DefaultChannels mDefaultChannels = new DefaultChannels();
	private final Map<UUID, ChannelSettings> mChannelSettings = new HashMap<>();

	private final Set<UUID> mIgnoredPlayers = new HashSet<>();

	private final Map<UUID, UUID> mWhisperChannelsByRecipient = new HashMap<>();
	private final Map<UUID, UUID> mWhisperRecipientByChannels = new HashMap<>();
	private @Nullable UUID mLastWhisperChannel;

	// Channels not in these maps will use the default channel watch status.
	private final Map<UUID, String> mWatchedChannelIds = new HashMap<>();
	private final Map<UUID, String> mUnwatchedChannelIds = new HashMap<>();

	private String mProfileMessage = "";

	public PlayerState(Player player) {
		mPlayerId = player.getUniqueId();
		mChatPaused = false;

		mLastWhisperChannel = null;

		unsetActiveChannel();
	}

	public static PlayerState fromJson(Player player, JsonObject obj) {
		PlayerState state = new PlayerState(player);

		Instant now = Instant.now();
		Long nowMillis = now.toEpochMilli();
		@Nullable Long lastLoginMillis = null;
		@Nullable JsonPrimitive lastLoginJson = obj.getAsJsonPrimitive("lastSaved");
		if (lastLoginJson != null) {
			try {
				lastLoginMillis = lastLoginJson.getAsLong();
			} catch (NumberFormatException e) {
				MMLog.warning("Could not get lastSaved time for player " + player.getName());
			}

			if (lastLoginMillis != null) {
				long millisOffline = nowMillis - lastLoginMillis;
				MMLog.finer(player.getName() + " was offline for " + millisOffline / 1000.0 + " seconds.");
			}
		}

		@Nullable JsonPrimitive isPausedJson = obj.getAsJsonPrimitive("isPaused");
		if (isPausedJson != null) {
			state.mChatPaused = isPausedJson.getAsBoolean();
		}

		@Nullable JsonPrimitive activeChannelJson = obj.getAsJsonPrimitive("activeChannel");
		if (activeChannelJson != null) {
			state.mActiveChannelId = UUID.fromString(activeChannelJson.getAsString());
		}

		@Nullable JsonArray ignoredPlayersJson = obj.getAsJsonArray("ignoredPlayers");
		if (ignoredPlayersJson != null) {
			for (JsonElement ignoredUuidJson : ignoredPlayersJson) {
				try {
					UUID ignoredUuid = UUID.fromString(ignoredUuidJson.getAsString());
					state.mIgnoredPlayers.add(ignoredUuid);
				} catch (Exception e) {
					MMLog.warning("Catch an exception while converting " + player.getName() + "'s ignoredPlayers to array. Reason: " + e.getMessage());
				}
			}
		}

		@Nullable JsonObject whisperChannelsJson = obj.getAsJsonObject("whisperChannels");
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
					MMLog.warning("Catch an exception while converting " + player.getName() + "'s whisperChannels to object. Reason: " + e.getMessage());
				}
			}
		}

		try {
			JsonPrimitive lastWhisperChannel = obj.getAsJsonPrimitive("lastWhisperChannel");
			state.mLastWhisperChannel = UUID.fromString(lastWhisperChannel.getAsString());
		} catch (Exception e) {
			state.mLastWhisperChannel = null;
		}

		@Nullable JsonObject watchedChannelsJson = obj.getAsJsonObject("watchedChannels");
		if (watchedChannelsJson != null) {
			for (Map.Entry<String, JsonElement> channelIdEntry : watchedChannelsJson.entrySet()) {
				String channelId = channelIdEntry.getKey();
				JsonElement lastKnownChannelName = channelIdEntry.getValue();
				try {
					state.mWatchedChannelIds.put(UUID.fromString(channelId), lastKnownChannelName.getAsString());
				} catch (Exception e) {
					MMLog.warning("Catch an exception while converting " + player.getName() + "'s watchedChannels to object. Reason: " + e.getMessage());
				}
			}
		}

		@Nullable JsonObject unwatchedChannelsJson = obj.getAsJsonObject("unwatchedChannels");
		if (unwatchedChannelsJson != null) {
			for (Map.Entry<String, JsonElement> channelIdEntry : unwatchedChannelsJson.entrySet()) {
				String channelId = channelIdEntry.getKey();
				JsonElement lastKnownChannelName = channelIdEntry.getValue();
				try {
					state.mUnwatchedChannelIds.put(UUID.fromString(channelId), lastKnownChannelName.getAsString());
				} catch (Exception e) {
					MMLog.warning("Catch an exception while converting " + player.getName() + "'s unwatchedChannels to object. Reason: " + e.getMessage());
				}
			}
		}

		@Nullable JsonObject defaultChannelSettingsJson = obj.getAsJsonObject("defaultChannelSettings");
		if (defaultChannelSettingsJson != null) {
			state.mDefaultChannelSettings = ChannelSettings.fromJson(defaultChannelSettingsJson);
		}

		@Nullable JsonObject defaultChannelsJson = obj.getAsJsonObject("defaultChannels");
		if (defaultChannelsJson != null) {
			state.mDefaultChannels = DefaultChannels.fromJson(defaultChannelsJson);
		}

		@Nullable JsonObject allChannelSettingsJson = obj.getAsJsonObject("channelSettings");
		if (allChannelSettingsJson != null) {
			for (Map.Entry<String, JsonElement> channelSettingEntry : allChannelSettingsJson.entrySet()) {
				String channelId = channelSettingEntry.getKey();
				JsonElement channelSettingJson = channelSettingEntry.getValue();
				try {
					ChannelSettings channelSettings = ChannelSettings.fromJson(channelSettingJson.getAsJsonObject());
					state.mChannelSettings.put(UUID.fromString(channelId), channelSettings);
				} catch (Exception e) {
					MMLog.warning("Catch an exception while converting " + player.getName() + "'s channelSettings to object. Reason: " + e.getMessage());
				}
			}
		}

		@Nullable JsonPrimitive profileMessageJson = obj.getAsJsonPrimitive("profileMessage");
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

		JsonArray ignoredPlayers = new JsonArray();
		for (UUID ignoredPlayer : mIgnoredPlayers) {
			ignoredPlayers.add(ignoredPlayer.toString());
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
		result.add("ignoredPlayers", ignoredPlayers);
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

	public @Nullable Player getPlayer() {
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
		@Nullable ChannelSettings channelSettings = mChannelSettings.get(channelId);
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
		@Nullable UUID senderId = message.getSenderId();
		if (senderId == null || !mIgnoredPlayers.contains(senderId)) {
			getPlayerChatHistory().receiveMessage(message);
		}
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

	public Set<UUID> getIgnoredPlayerIds() {
		return mIgnoredPlayers;
	}

	public Set<String> getIgnoredPlayerNames() {
		Set<String> ignoredNames = new TreeSet<>();
		for (UUID ignoredId : mIgnoredPlayers) {
			@Nullable String ignoredName = MonumentaRedisSyncAPI.cachedUuidToName(ignoredId);
			if (ignoredName == null) {
				MMLog.warning("Could not get name of ignored player with UUID " + ignoredId.toString());
			} else {
				ignoredNames.add(ignoredName);
			}
		}
		return ignoredNames;
	}

	public @Nullable ChannelWhisper getWhisperChannel(UUID recipientUuid) throws WrapperCommandSyntaxException {
		@Nullable UUID channelId = mWhisperChannelsByRecipient.get(recipientUuid);
		@Nullable Channel channel;

		if (channelId == null) {
			ArrayList<UUID> participants = new ArrayList<>();
			participants.add(mPlayerId);
			participants.add(recipientUuid);

			String channelName = ChannelWhisper.getAltName(participants);
			channelId = ChannelManager.getChannelId(channelName);

			if (channelId == null) {
				channelName = ChannelWhisper.getName(participants);
				channelId = ChannelManager.getChannelId(channelName);
			}
		}

		if (channelId == null) {
			return null;
		}

		channel = ChannelManager.loadChannel(channelId);
		if (!(channel instanceof ChannelWhisper channelWhisper)) {
			throw CommandAPI.failWithString("Whisper channel is loading, please try again.");
		} else {
			return channelWhisper;
		}
	}

	public void setWhisperChannel(UUID recipientUuid, ChannelWhisper channel) {
		UUID channelUuid = channel.getUniqueId();
		@Nullable UUID oldChannelUuid = mWhisperChannelsByRecipient.get(recipientUuid);
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

	public @Nullable ChannelWhisper getLastWhisperChannel() {
		if (mLastWhisperChannel == null) {
			return null;
		}
		@Nullable Channel channel = ChannelManager.getChannel(mLastWhisperChannel);
		if (!(channel instanceof ChannelWhisper)) {
			return null;
		}
		return (ChannelWhisper) channel;
	}

	public boolean hasNotSeenChannelId(UUID channelId) {
		return !mWatchedChannelIds.containsKey(channelId)
			&& !mUnwatchedChannelIds.containsKey(channelId);
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
		if (channelId.equals(mActiveChannelId)) {
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
		@Nullable UUID whisperRecipientUuid = mWhisperRecipientByChannels.get(channelId);
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
		@Nullable Channel channel = mDefaultChannels.getDefaultChannel(channelType);
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
		Player player = getPlayer();
		if (player == null) {
			return false;
		}
		switch (player.getClientOption(ClientOption.CHAT_VISIBILITY)) {
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

		@Nullable ChannelSettings channelSettings = mChannelSettings.get(channelId);
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
		if (getPlayerChatHistory().isReplayingChat()) {
			return;
		}

		boolean shouldPlaySound = false;

		@Nullable Channel channel = message.getChannel();
		if (channel == null) {
			return;
		}
		@Nullable Player player = getPlayer();
		if (player == null) {
			return;
		}
		String plainMessage = message.getPlainMessage();
		if (MessagingUtils.isPlayerMentioned(plainMessage, player.getName())) {
			shouldPlaySound = true;
		} else if (plainMessage.contains("@everyone")) {
			shouldPlaySound = true;
		}
		if (shouldPlaySound) {
			player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.1f, 0.5f);
			return;
		}

		UUID channelId = channel.getUniqueId();
		@Nullable ChannelSettings channelSettings = mChannelSettings.get(channelId);
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
		Set<Channel> channels = new HashSet<>();
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
		@Nullable String newChannelName = null;
		if (channel != null) {
			newChannelName = channel.getName();
		}
		@Nullable String lastKnownName = mWatchedChannelIds.get(channelId);
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
		@Nullable UUID whisperRecipientUuid;
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
				Player player = getPlayer();
				if (player != null) {
					player.sendMessage(Component.text("The channel you knew as " + lastKnownName + " is no longer available.", NamedTextColor.RED));
				}
			}
		} else {
			if (showAlert && !lastKnownName.equals(newChannelName)) {
				Player player = getPlayer();
				if (player != null) {
					player.sendMessage(Component.text("The channel you knew as " + lastKnownName + " is now known as " + newChannelName + ".", NamedTextColor.GRAY));
				}
			}
		}
	}

	public Component profileMessageComponent() {
		@Nullable Player player = getPlayer();
		if (player == null) {
			return Component.empty();
		}
		return MessagingUtils.getAllowedMiniMessage(player).deserialize(mProfileMessage);
	}

	public String profileMessage() {
		return mProfileMessage;
	}

	public void profileMessage(String profileMessage) {
		@Nullable Player player = getPlayer();
		if (player == null) {
			return;
		}
		if (profileMessage == null) {
			mProfileMessage = "";
			RemotePlayerManager.refreshLocalPlayer(player);
			return;
		}

		Component profileMessageComponent = MessagingUtils.getAllowedMiniMessage(player).deserialize(profileMessage);
		if (NetworkChatPlugin.globalFilter().hasBadWord(player, profileMessageComponent)) {
			return;
		}

		mProfileMessage = profileMessage;
		RemotePlayerManager.refreshLocalPlayer(player);
	}
}
