package com.playmonumenta.networkchat;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkchat.utils.FileUtils;
import com.playmonumenta.redissync.RedisAPI;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisAsyncCommands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public class ChannelManager {
	private static final String REDIS_CHANNEL_NAME_TO_UUID_PATH = "networkchat:channel_name_to_uuids";
	private static final String REDIS_CHANNELS_PATH = "networkchat:channels";
	private static final String REDIS_CHANNEL_PARTICIPANTS_PATH = "networkchat:channel_participants";

	private static ChannelManager INSTANCE = null;
	private static Plugin mPlugin = null;
	private static File mServerChannelConfigFile;
	private static UUID mDefaultChannel;
	private static Set<UUID> mForceLoadedChannels = new HashSet<>();
	private static Map<String, UUID> mChannelIdsByName = null;
	private static Map<UUID, Channel> mChannels = new HashMap<>();

	private ChannelManager(Plugin plugin) {
		INSTANCE = this;
		mPlugin = plugin;
		mServerChannelConfigFile = new File(plugin.getDataFolder(), "serverChannelConfig.json");
		reload();
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

	public static void reload() {
		// Load the list of forceloaded channels
		JsonObject serverChannelConfigJson;
		try {
			serverChannelConfigJson = FileUtils.readJson(mServerChannelConfigFile.getPath());
		} catch (Exception e) {
			mPlugin.getLogger().warning("Could not load server channel config; assuming file does not exist yet.");
			return;
		}

		Set<UUID> previouslyForceLoaded = mForceLoadedChannels;
		mForceLoadedChannels = new HashSet<>();

		mDefaultChannel = null;
		JsonPrimitive defaultChannelJson = serverChannelConfigJson.getAsJsonPrimitive("defaultChannel");
		try {
			mDefaultChannel = UUID.fromString(defaultChannelJson.getAsString());
			mForceLoadedChannels.add(mDefaultChannel);
			loadChannel(mDefaultChannel, (PlayerState) null);
		} catch (Exception e) {
			mPlugin.getLogger().warning("Could not get default channel. Configure with /chattest.");
		}

		JsonObject forceLoadJson = serverChannelConfigJson.getAsJsonObject("forceLoadedChannels");
		if (forceLoadJson != null) {
			for (Map.Entry<String, JsonElement> forceLoadEntry : forceLoadJson.entrySet()) {
				String channelIdStr = forceLoadEntry.getKey();
				try {
					UUID channelId = UUID.fromString(channelIdStr);
					mForceLoadedChannels.add(channelId);
					loadChannel(channelId, (PlayerState) null);
				} catch (Exception e) {
					mPlugin.getLogger().warning("Could not force-load channel ID " + channelIdStr + ".");
					continue;
				}
			}
		}

		// TODO Unload channels from previouslyForceLoaded if appropriate
	}

	public static void saveConfig() {
		JsonObject forceLoadJson = new JsonObject();
		for (UUID channelId : mForceLoadedChannels) {
			Channel channel = mChannels.get(channelId);
			if (channel == null) {
				continue;
			}
			String channelName = channel.getName();
			forceLoadJson.addProperty(channelId.toString(), channelName);
		}

		JsonObject serverChannelConfigJson = new JsonObject();
		serverChannelConfigJson.addProperty("defaultChannel", mDefaultChannel.toString());
		serverChannelConfigJson.add("forceLoadedChannels", forceLoadJson);

		try {
			FileUtils.writeJson(mServerChannelConfigFile.getPath(), serverChannelConfigJson);
		} catch (Exception e) {
			mPlugin.getLogger().warning("Could not save server channel config.");
		}
	}

	public static Set<UUID> getLoadedChannelIds() {
		return new HashSet<>(mChannels.keySet());
	}

	public static Set<String> getChannelNames() {
		return new HashSet<>(mChannelIdsByName.keySet());
	}

	// Used for new channels
	public static void registerNewChannel(CommandSender sender, Channel channel) throws WrapperCommandSyntaxException {
		if (channel == null) {
			return;
		}

		String channelName = channel.getName();
		UUID oldChannelId = mChannelIdsByName.get(channelName);
		if (oldChannelId != null) {
			CommandAPI.fail("Channel " + channelName + " already exists!");
		}
		UUID channelId = channel.getUniqueId();
		RedisAPI.getInstance().async().hset(REDIS_CHANNEL_NAME_TO_UUID_PATH, channelName, channelId.toString());
		mChannelIdsByName.put(channelName, channelId);
		mChannels.put(channelId, channel);
		saveChannel(channel);
	}

	// Used for channels that are done loading from Redis
	public static void registerLoadedChannel(Channel channel) {
		if (channel == null) {
			return;
		}

		UUID channelId = channel.getUniqueId();

		// Unregister any old channels as needed.
		Channel oldChannel = mChannels.get(channelId);
		boolean sendQueuedMessages = false;
		if (oldChannel != null) {
			if (oldChannel instanceof ChannelLoading) {
				// It's safe to replace a loading channel with a loading one.
				sendQueuedMessages = true;
			} else {
				// Unregister the old channel so the new one can load.
				PlayerStateManager.unregisterChannel(channelId);
				mChannels.remove(channelId);
			}
		}

		// Continue registering the loaded channel.
		String channelName = channel.getName();
		mChannels.put(channelId, channel);

		if (sendQueuedMessages) {
			// TODO Send queued messages to players waiting on them
			;
		}
	}

	public static Channel getDefaultChannel() {
		return mChannels.get(mDefaultChannel);
	}

	public static Channel getChannel(UUID channelId) {
		return mChannels.get(channelId);
	}

	public static Channel getChannel(String channelName) {
		UUID channelId = mChannelIdsByName.get(channelName);
		if (channelId == null) {
			return null;
		}
		return mChannels.get(channelId);
	}

	public static void renameChannel(String oldName, String newName) throws WrapperCommandSyntaxException {
		UUID oldChannelId = mChannelIdsByName.get(oldName);
		if (oldChannelId == null) {
			CommandAPI.fail("Channel " + oldName + " does not exist!");
		}
		Channel channel = mChannels.get(oldChannelId);
		if (channel == null || channel instanceof ChannelLoading) {
			loadChannel(oldChannelId, (PlayerState) null);
			CommandAPI.fail("Channel " + oldName + " not yet loaded, try again.");
		}

		if (mChannelIdsByName.get(newName) != null) {
			CommandAPI.fail("Channel " + newName + " already exists!");
		}

		// NOTE: May call CommandAPI.fail to cancel the change before it occurs.
		channel.setName(newName);

		mChannelIdsByName.put(newName, channel.getUniqueId());
		mChannelIdsByName.remove(oldName);

		// TODO Broadcast the change

		saveChannel(channel);
		RedisAPI.getInstance().async().hdel(REDIS_CHANNEL_NAME_TO_UUID_PATH, oldName);
	}

	public static void deleteChannel(String channelName) throws WrapperCommandSyntaxException {
		Channel channel = getChannel(channelName);
		if (channel == null) {
			CommandAPI.fail("Channel " + channelName + " does not exist!");
		}

		UUID channelId = channel.getUniqueId();
		for (PlayerState playerState : PlayerStateManager.getPlayerStates().values()) {
			playerState.unregisterChannel(channelId);
		}

		mChannels.remove(channelId);

		// TODO Broadcast the change to other shards

		RedisAsyncCommands<String, String> redisAsync = RedisAPI.getInstance().async();
		redisAsync.hdel(REDIS_CHANNEL_NAME_TO_UUID_PATH, channelName);
		redisAsync.hdel(REDIS_CHANNELS_PATH, channelId.toString());
		redisAsync.hdel(REDIS_CHANNEL_PARTICIPANTS_PATH, channelId.toString());
	}

	public static int setDefaultChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		UUID channelId = mChannelIdsByName.get(channelName);
		if (channelId == null) {
			CommandAPI.fail("Channel " + channelName + " does not exist!");
		}

		loadChannel(channelId, (PlayerState) null);
		mDefaultChannel = channelId;
		mForceLoadedChannels.add(channelId);
		saveConfig();
		sender.sendMessage(Component.text("Channel " + channelName + " is now the default channel.", NamedTextColor.GRAY));
		return 1;
	}

	public static int forceLoadChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		UUID channelId = mChannelIdsByName.get(channelName);
		if (channelId == null) {
			CommandAPI.fail("Channel " + channelName + " does not exist!");
		}

		loadChannel(channelId, (PlayerState) null);
		mForceLoadedChannels.add(channelId);
		saveConfig();
		sender.sendMessage(Component.text("Channel " + channelName + " has been force loaded.", NamedTextColor.GRAY));
		return 1;
	}

	public static int unforceLoadChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		UUID channelId = mChannelIdsByName.get(channelName);
		if (channelId == null) {
			CommandAPI.fail("Channel " + channelName + " does not exist!");
		}

		mForceLoadedChannels.remove(channelId);
		saveConfig();
		sender.sendMessage(Component.text("Channel " + channelName + " is no longer force loaded.", NamedTextColor.GRAY));
		return 1;
	}

	public static void loadChannel(UUID channelId, PlayerState playerState) {
		/* Note that mChannels containing the channel ID means
		 * either the channel is loaded, or is being loaded. */
		Channel preloadedChannel = mChannels.get(channelId);
		if (preloadedChannel != null) {
			if (preloadedChannel instanceof ChannelLoading && playerState != null) {
				((ChannelLoading) preloadedChannel).addWaitingPlayerState(playerState);
			}
			return;
		}
		Channel channel = null;

		// Mark the channel as loading
		mPlugin.getLogger().info("Attempting to load channel " + channelId.toString() + ".");
		ChannelLoading loadingChannel = new ChannelLoading(channelId);
		mChannels.put(channelId, loadingChannel);
		loadingChannel.addWaitingPlayerState(playerState);

		// Get the channel from Redis async
		String channelIdStr = channelId.toString();
		// TODO Consider returning RedisFuture so PlayerState can handle this directly?
		RedisFuture<String> channelDataFuture = RedisAPI.getInstance().async().hget(REDIS_CHANNELS_PATH, channelIdStr);
		channelDataFuture.thenApply(channelData -> {
			loadChannelApply(channelId, channelData);
			return channelData;
		});
	}

	public static void loadChannel(UUID channelId, Message message) {
		/* Note that mChannels containing the channel ID means
		 * either the channel is loaded, or is being loaded. */
		Channel preloadedChannel = mChannels.get(channelId);
		if (preloadedChannel != null) {
			if (preloadedChannel instanceof ChannelLoading && message != null) {
				((ChannelLoading) preloadedChannel).addMessage(message);
			}
			return;
		}
		Channel channel = null;

		// Mark the channel as loading
		mPlugin.getLogger().info("Attempting to load channel " + channelId.toString() + ".");
		ChannelLoading loadingChannel = new ChannelLoading(channelId);
		mChannels.put(channelId, loadingChannel);
		loadingChannel.addMessage(message);

		// Get the channel from Redis async
		String channelIdStr = channelId.toString();
		// TODO Consider returning RedisFuture so Message can handle this directly?
		RedisFuture<String> channelDataFuture = RedisAPI.getInstance().async().hget(REDIS_CHANNELS_PATH, channelIdStr);
		channelDataFuture.thenApply(channelData -> {
			loadChannelApply(channelId, channelData);
			return channelData;
		});
	}

	private static void loadChannelApply(UUID channelId, String channelData) {
		Channel channel = mChannels.get(channelId);
		if (!(channel instanceof ChannelLoading)) {
			// Channel already finished loading.
			return;
		}

		Gson gson = new Gson();
		ChannelLoading loadingChannel = (ChannelLoading) channel;
		if (channelData == null) {
			// No channel was found, alert the player it was deleted.
			mPlugin.getLogger().warning("Channel " + channelId.toString() + " was not found.");
			PlayerStateManager.unregisterChannel(channelId);
			mChannels.remove(channelId);
			loadingChannel.finishLoading();
			return;
		} else {
			// Channel was found. Attempt to register it.
			JsonObject channelJson = gson.fromJson(channelData, JsonObject.class);
			try {
				channel = Channel.fromJson(channelJson);
				mPlugin.getLogger().info("Channel " + channelId.toString() + " loaded, registering...");
			} catch (Exception e) {
				mPlugin.getLogger().severe("Caught exception trying to load channel " + channelId.toString() + ": " + e);
				return;
			}
			registerLoadedChannel(channel);
			loadingChannel.finishLoading();
			saveConfig();
		}
	}

	public static void saveChannel(Channel channel) {
		if (channel == null || channel instanceof ChannelLoading || channel instanceof ChannelFuture) {
			return;
		}

		String channelIdStr = channel.getUniqueId().toString();
		String channelName = channel.getName();
		String channelJsonStr = channel.toJson().toString();

		mPlugin.getLogger().info("Saving channel " + channelIdStr + ".");
		RedisAsyncCommands<String, String> redisAsync = RedisAPI.getInstance().async();
		redisAsync.hset(REDIS_CHANNEL_NAME_TO_UUID_PATH, channelName, channelIdStr);
		redisAsync.hset(REDIS_CHANNELS_PATH, channelIdStr, channelJsonStr);
	}

	public static void unloadChannel(Channel channel) {
		UUID channelId = channel.getUniqueId();
		String channelName = channel.getName();

		for (PlayerState playerState : PlayerStateManager.getPlayerStates().values()) {
			if (playerState.isWatchingChannelId(channelId)) {
				// Abort unload attempt
				return;
			}
		}

		mChannels.remove(channelId);
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

	protected static void resetAll() {
		RedisAPI.getInstance().async().del(REDIS_CHANNEL_NAME_TO_UUID_PATH);
		RedisAPI.getInstance().async().del(REDIS_CHANNELS_PATH);
		RedisAPI.getInstance().async().del(REDIS_CHANNEL_PARTICIPANTS_PATH);
	}
}
