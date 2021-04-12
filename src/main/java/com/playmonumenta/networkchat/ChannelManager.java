package com.playmonumenta.networkchat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.playmonumenta.redissync.RedisAPI;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisAsyncCommands;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class ChannelManager {
	private static final String REDIS_CHANNEL_NAME_TO_UUID_PATH = "networkchat:channel_name_to_uuids";
	private static final String REDIS_CHANNELS_PATH = "networkchat:channels";
	private static final String REDIS_CHANNEL_PARTICIPANTS_PATH = "networkchat:channel_participants";

	private static ChannelManager INSTANCE = null;
	private static Plugin mPlugin = null;
	private static Map<String, UUID> mChannelIdsByName = null;
	private static Map<UUID, ChannelBase> mChannels = new HashMap<>();
	private static Map<String, ChannelBase> mChannelsByName = new HashMap<>();

	private ChannelManager(Plugin plugin) {
		INSTANCE = this;
		mPlugin = plugin;
		loadAllChannelNames();
	}

	public static ChannelManager getInstance() {
		return INSTANCE;
	}

	public static ChannelManager getInstance(Plugin plugin) {
		if (INSTANCE == null) {
			INSTANCE = new ChannelManager(plugin);
		}
		return INSTANCE;
	}

	public static Set<UUID> getLoadedChannelIds() {
		return new HashSet<>(mChannels.keySet());
	}

	public static Set<String> getChannelNames() {
		return new HashSet<>(mChannelsByName.keySet());
	}

	public static void registerNewChannel(ChannelBase channel) throws WrapperCommandSyntaxException {
		if (channel == null) {
			return;
		}

		String channelName = channel.getName();
		ChannelBase oldChannel = mChannelsByName.get(channelName);
		if (oldChannel != null) {
			CommandAPI.fail("Channel " + channelName + " already exists!");
		}
		UUID channelId = channel.getUniqueId();
		mChannels.put(channelId, channel);
		mChannelsByName.put(channelName, channel);
	}

	public static void registerLoadedChannel(ChannelBase channel) {
		if (channel == null) {
			return;
		}

		UUID channelId = channel.getUniqueId();

		// Unregister any old channels as needed.
		ChannelBase oldChannel = mChannels.get(channelId);
		boolean sendQueuedMessages = false;
		if (oldChannel != null) {
			if (oldChannel instanceof ChannelLoading) {
				// It's safe to replace a loading channel with a loading one.
				sendQueuedMessages = true;
			} else {
				// Unregister the old channel so the new one can load.
				PlayerStateManager.unregisterChannel(channelId);
				mChannelsByName.remove(oldChannel.getName());
				mChannels.remove(channelId);
			}
		}

		// Continue registering the loaded channel.
		String channelName = channel.getName();
		mChannels.put(channelId, channel);
		mChannelsByName.put(channelName, channel);

		if (sendQueuedMessages) {
			// TODO Send queued messages to players waiting on them
			;
		}
	}

	public static ChannelBase getChannel(UUID channelId) {
		return mChannels.get(channelId);
	}

	public static ChannelBase getChannel(String channelName) {
		return mChannelsByName.get(channelName);
	}

	public static void renameChannel(String oldName, String newName) throws WrapperCommandSyntaxException {
		ChannelBase channel = mChannelsByName.get(oldName);
		if (channel == null) {
			CommandAPI.fail("Channel " + oldName + " does not exist!");
		}

		if (mChannelsByName.get(newName) != null) {
			CommandAPI.fail("Channel " + newName + " already exists!");
		}

		// NOTE: May call CommandAPI.fail to cancel the change before it occurs.
		channel.setName(newName);

		mChannelsByName.put(newName, channel);
		mChannelsByName.remove(oldName);

		// TODO Broadcast the change

		saveChannel(channel);
	}

	public static void deleteChannel(String channelName) throws WrapperCommandSyntaxException {
		ChannelBase channel = mChannelsByName.get(channelName);
		if (channel == null) {
			CommandAPI.fail("Channel " + channelName + " does not exist!");
		}

		UUID channelId = channel.getUniqueId();
		for (PlayerState playerState : PlayerStateManager.getPlayerStates().values()) {
			playerState.unregisterChannel(channelId);
		}

		mChannels.remove(channelId);
		mChannelsByName.remove(channelName);

		// TODO Broadcast the change to other shards

		RedisAPI.getInstance().async().hdel(REDIS_CHANNELS_PATH, channelId.toString());
		RedisAPI.getInstance().async().hdel(REDIS_CHANNEL_PARTICIPANTS_PATH, channelId.toString());
	}

	public static void loadChannel(UUID channelId, PlayerState playerState) {
		/* Note that mChannels containing the channel ID means
		 * either the channel is loaded, or is being loaded. */
		ChannelBase preloadedChannel = mChannels.get(channelId);
		if (preloadedChannel != null) {
			if (preloadedChannel instanceof ChannelLoading) {
				((ChannelLoading) preloadedChannel).addWaitingPlayerState(playerState);
			}
			return;
		}
		ChannelBase channel = null;

		// Mark the channel as loading
		ChannelLoading loadingChannel = new ChannelLoading(channelId);
		mChannels.put(channelId, loadingChannel);

		// Get the channel from Redis async
		String channelIdStr = channelId.toString();
		// TODO Consider returning RedisFuture so PlayerState can handle this directly?
		RedisFuture<String> channelDataFuture = RedisAPI.getInstance().async().hget(REDIS_CHANNELS_PATH, channelIdStr);
		channelDataFuture.thenApply(channelData -> {
			loadChannelApply(channelId, channelData);
			return channelData;
		});
	}

	private static void loadChannelApply(UUID channelId, String channelData) {
		ChannelBase channel = mChannels.get(channelId);
		if (!(channel instanceof ChannelLoading)) {
			// Channel already finished loading.
			return;
		}

		Gson gson = new Gson();
		ChannelLoading loadingChannel = (ChannelLoading) channel;
		if (channelData == null) {
			// No channel was found, alert the player it was deleted.
			PlayerStateManager.unregisterChannel(channelId);
			mChannels.remove(channelId);
			loadingChannel.alertPlayerStates();
			return;
		} else {
			// Channel was found. Attempt to register it.
			JsonObject channelJson = gson.fromJson(channelData, JsonObject.class);
			try {
				channel = ChannelBase.fromJson(channelJson);
			} catch (Exception e) {
				mPlugin.getLogger().severe("Caught exception trying to load channel " + channelId.toString() + ": " + e);
				return;
			}
			registerLoadedChannel(channel);
			loadingChannel.alertPlayerStates();
		}
	}

	public static void saveChannel(ChannelBase channel) {
		if (channel == null || channel instanceof ChannelLoading || channel instanceof ChannelFuture) {
			return;
		}

		String channelIdStr = channel.getUniqueId().toString();
		String channelJsonStr = channel.toJson().toString();
		RedisAPI.getInstance().async().hset(REDIS_CHANNELS_PATH, channelIdStr, channelJsonStr);
	}

	public static void unloadChannel(ChannelBase channel) {
		UUID channelId = channel.getUniqueId();
		String channelName = channel.getName();

		for (PlayerState playerState : PlayerStateManager.getPlayerStates().values()) {
			if (playerState.isWatchingChannelId(channelId)) {
				// Abort unload attempt
				return;
			}
		}

		mChannels.remove(channelId);
		mChannelsByName.remove(channelName);
		saveChannel(channel);

		// No announcements or deletion to do.
	}

	private static void loadAllChannelNames() {
		RedisFuture<Map<String, String>> channelDataFuture = RedisAPI.getInstance().async().hgetall(REDIS_CHANNEL_NAME_TO_UUID_PATH);
		channelDataFuture.thenApply(channelStrIdsByName -> {
			mChannelIdsByName = new HashMap<>();
			if (channelStrIdsByName != null) {
				for (Map.Entry<String, String> entry : channelStrIdsByName.entrySet()) {
					String channelName = entry.getKey();
					String channelIdStr = entry.getValue();
					UUID channelId = UUID.fromString(channelIdStr);
					mChannelIdsByName.put(channelName, channelId);
				}
			}
			return channelStrIdsByName;
		});
	}
}
