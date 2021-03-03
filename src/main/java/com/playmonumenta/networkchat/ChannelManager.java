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

import io.lettuce.core.api.async.RedisAsyncCommands;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class ChannelManager {
	private static final String REDIS_CHANNELS_PATH = "networkchat:channels";

	private static ChannelManager INSTANCE = null;
	private static Plugin mPlugin = null;
	private static Map<UUID, ChannelBase> mChannels = new HashMap<>();
	private static Map<String, ChannelBase> mChannelsByName = new HashMap<>();

	private ChannelManager(Plugin plugin) {
		INSTANCE = this;
		mPlugin = plugin;
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
		UUID uuid = channel.getUniqueId();
		mChannels.put(uuid, channel);
		mChannelsByName.put(channelName, channel);
	}

	public static void registerLoadedChannel(ChannelBase channel) {
		if (channel == null) {
			return;
		}

		UUID uuid = channel.getUniqueId();

		// Unregister any old channels as needed.
		ChannelBase oldChannel = mChannels.get(uuid);
		boolean sendQueuedMessages = false;
		if (oldChannel != null) {
			if (oldChannel instanceof ChannelLoading) {
				// It's safe to replace a loading channel with a loading one.
				sendQueuedMessages = true;
			} else {
				// TODO unregister the old channel so the new one can load.
			}
		}

		// Continue registering the loaded channel.
		String channelName = channel.getName();
		mChannels.put(uuid, channel);
		mChannelsByName.put(channelName, channel);

		if (sendQueuedMessages) {
			// TODO
			;
		}
	}

	public static ChannelBase getChannel(UUID channelUuid) {
		return mChannels.get(channelUuid);
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
	}

	public static void loadChannel(UUID channelId) {
		/* Note that mChannels containing the channel ID means
		 * either the channel is loaded, or is being loaded. */
		if (channelId == null || mChannels.containsKey(channelId)) {
			return;
		}

		// Mark the channel as loading
		mChannels.put(channelId, new ChannelLoading(channelId));

		// Get the channel from Redis async
		String channelIdStr = channelId.toString();
		Bukkit.getServer().getScheduler().runTaskAsynchronously(mPlugin, () -> {
			RedisAPI api = RedisAPI.getInstance();
			String jsonData = api.sync().hget(REDIS_CHANNELS_PATH, channelIdStr);
			if (jsonData == null) {
				Bukkit.getServer().getScheduler().runTask(mPlugin, () -> {
					// No channel was found, alert the player it was deleted.
				});
			} else {
				Gson gson = new Gson();
				final JsonObject channelJson = gson.fromJson(jsonData, JsonObject.class);
				Bukkit.getServer().getScheduler().runTask(mPlugin, () -> {
					// Channel json was found, load it in sync.
					ChannelBase channel = null;
					try {
						channel = ChannelBase.fromJson(channelJson);
					} catch (Exception e) {
						/* TODO Log the error, and somehow mark the channel as "exists, but can't load"
						 * Messages will be ignored, but assume that it's a type from a future plugin version,
						 * and don't remove the channel from players. */
						return;
					}

					registerLoadedChannel(channel);
				});
			}
		});
	}

	public static void saveChannel(ChannelBase channel) {
		if (channel == null || channel instanceof ChannelLoading) {
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
}
