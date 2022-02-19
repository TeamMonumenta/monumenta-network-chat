package com.playmonumenta.networkchat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.networkrelay.DestOfflineEvent;
import com.playmonumenta.networkrelay.DestOnlineEvent;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;

import javax.annotation.Nullable;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class RemotePlayerManager implements Listener {
	static class RemotePlayerState {
		public final UUID mUuid;
		public final String mName;
		public final Component mComponent;
		public final boolean mIsHidden;
		public final boolean mIsOnline;
		public final String mShard;

	    public RemotePlayerState(Plugin plugin, Player player, boolean isOnline) {
		    mUuid = player.getUniqueId();
		    mName = player.getName();
		    mComponent = MessagingUtils.playerComponent(player);
		    mIsHidden = !RemotePlayerManager.isLocalPlayerVisible(player);
		    mIsOnline = isOnline;
		    mShard = RemotePlayerManager.getShardName();

			plugin.getLogger().info("Created RemotePlayerState for " + mName + " from " + mShard + ": " + (mIsOnline ? "online" : "offline"));
	    }

	    public RemotePlayerState(Plugin plugin, JsonObject remoteData) throws Exception {
			mUuid = UUID.fromString(remoteData.get("playerUuid").getAsString());
			mName = remoteData.get("playerName").getAsString();
			mComponent = MessagingUtils.fromJson(remoteData.get("playerComponent"));
			mIsHidden = remoteData.get("isHidden").getAsBoolean();
			mIsOnline = remoteData.get("isOnline").getAsBoolean();
			mShard = remoteData.get("shard").getAsString();

			plugin.getLogger().info("Recieved RemotePlayerState for " + mName + " from " + mShard + ": " + (mIsOnline ? "online" : "offline"));
	    }

	    public void broadcast() {
		    JsonObject remotePlayerData = new JsonObject();
		    remotePlayerData.addProperty("playerUuid", mUuid.toString());
		    remotePlayerData.addProperty("playerName", mName);
		    remotePlayerData.add("playerComponent", MessagingUtils.toJson(mComponent));
		    remotePlayerData.addProperty("isHidden", mIsHidden);
		    remotePlayerData.addProperty("isOnline", mIsOnline);
		    remotePlayerData.addProperty("shard", mShard);

		    try {
			    NetworkRelayAPI.sendExpiringBroadcastMessage(RemotePlayerManager.REMOTE_PLAYER_CHANNEL,
			                                                 remotePlayerData,
			                                                 NetworkChatPlugin.getMessageTtl());
		    } catch (Exception e) {
			    mPlugin.getLogger().severe("Failed to broadcast " + RemotePlayerManager.REMOTE_PLAYER_CHANNEL);
		    }
	    }
	}

	public static final String REMOTE_PLAYER_CHANNEL = "com.playmonumenta.networkchat.RemotePlayerManager.remoteplayer";
	public static final String REFRESH_CHANNEL = "com.playmonumenta.networkchat.RemotePlayerManager.refresh";

	private static @Nullable RemotePlayerManager INSTANCE = null;
	private static Plugin mPlugin = null;
	private static @Nullable String mShardName = null;
	private static Map<String, Map<String, RemotePlayerState>> mRemotePlayersByShard = new ConcurrentSkipListMap<>();
	private static Map<UUID, RemotePlayerState> mPlayersByUuid = new ConcurrentSkipListMap<>();
	private static Map<String, RemotePlayerState> mPlayersByName = new ConcurrentSkipListMap<>();
	private static Set<UUID> mVisiblePlayers = new ConcurrentSkipListSet<>();

	private RemotePlayerManager(Plugin plugin) {
		INSTANCE = this;
		mPlugin = plugin;
		try {
			mShardName = NetworkRelayAPI.getShardName();
		} catch (Exception e) {
			mPlugin.getLogger().severe("Failed to get shard name");
		}
		if (mShardName == null) {
			throw new RuntimeException("Got null shard name");
		}
		try {
			for (String shard : NetworkRelayAPI.getOnlineShardNames()) {
				if (shard.equals(mShardName)) {
					continue;
				}
				mPlugin.getLogger().info("Registering shard " + shard);
				mRemotePlayersByShard.put(shard, new ConcurrentSkipListMap<>());
			}
		} catch (Exception e) {
			mPlugin.getLogger().severe("Failed to get remote shard names");
		}
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(REFRESH_CHANNEL,
			                                             new JsonObject(),
			                                             NetworkChatPlugin.getMessageTtl());
		} catch (Exception e) {
			mPlugin.getLogger().severe("Failed to broadcast " + REFRESH_CHANNEL);
		}
	}

	public static @Nullable RemotePlayerManager getInstance() {
		return INSTANCE;
	}

	public static RemotePlayerManager getInstance(Plugin plugin) {
		if (INSTANCE == null) {
			INSTANCE = new RemotePlayerManager(plugin);
		}
		return INSTANCE;
	}

	public static @Nullable String getShardName() {
		return mShardName;
	}

	public static Set<String> onlinePlayerNames() {
		return new ConcurrentSkipListSet<>(mPlayersByName.keySet());
	}

	public static boolean isPlayerOnline(UUID playerId) {
		return mPlayersByUuid.containsKey(playerId);
	}

	public static boolean isPlayerOnline(String playerName) {
		return mPlayersByName.containsKey(playerName);
	}

	public static Set<String> visiblePlayerNames() {
		Set<String> results = new ConcurrentSkipListSet<>();
		for (UUID playerId : mVisiblePlayers) {
			results.add(getPlayerName(playerId));
		}
		return results;
	}

	public static boolean isLocalPlayerVisible(Player player) {
		for (MetadataValue meta : player.getMetadata("vanished")) {
			if (meta.asBoolean()) {
				return false;
			}
		}
		return true;
	}

	public static boolean isPlayerVisible(Player player) {
		boolean cachedResult = isPlayerVisible(player.getUniqueId());
		boolean currentResult = isLocalPlayerVisible(player);
		if (cachedResult ^ currentResult) {
			refreshLocalPlayer(player);
		}
		return currentResult;
	}

	public static boolean isPlayerVisible(UUID playerId) {
		return mVisiblePlayers.contains(playerId);
	}

	public static boolean isPlayerVisible(String playerName) {
		@Nullable UUID playerId = getPlayerId(playerName);
		if (playerId == null) {
		    return false;
		}
		return isPlayerVisible(playerId);
	}

	public static @Nullable String getPlayerName(UUID playerUuid) {
		@Nullable RemotePlayerState remotePlayerState = mPlayersByUuid.get(playerUuid);
		if (remotePlayerState == null) {
		    return null;
		}
		return remotePlayerState.mName;
	}

	public static Component getPlayerComponent(UUID playerUuid) {
		@Nullable RemotePlayerState remotePlayerState = mPlayersByUuid.get(playerUuid);
		if (remotePlayerState == null || remotePlayerState.mIsHidden) {
			// Note: offline players are not visible
			@Nullable String playerName = MonumentaRedisSyncAPI.cachedUuidToName(playerUuid);
			if (playerName != null) {
				return Component.text(playerName, NamedTextColor.RED)
					.hoverEvent(Component.text("Offline", NamedTextColor.RED));
			}
			return Component.empty();
		}
		return remotePlayerState.mComponent;
	}

	public static @Nullable UUID getPlayerId(String playerName) {
		return MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
	}

	public static @Nullable String getPlayerShard(UUID playerId) {
		@Nullable RemotePlayerState remotePlayerState = mPlayersByUuid.get(playerId);
		if (remotePlayerState == null) {
		    return null;
		}
		return remotePlayerState.mShard;
	}

	public static void showOnlinePlayers(Audience audience) {
		boolean lightRow = true;
		boolean firstName;
		// Sorts as it goes
		Map<String, Component> shardPlayers;

		audience.sendMessage(Component.text("Online players:", NamedTextColor.DARK_BLUE));

		// Local first
		shardPlayers = new ConcurrentSkipListMap<>();
		for (Player player : Bukkit.getOnlinePlayers()) {
			@Nullable RemotePlayerState remotePlayerState = mPlayersByUuid.get(player.getUniqueId());
			if (remotePlayerState != null && !remotePlayerState.mIsHidden) {
			    shardPlayers.put(remotePlayerState.mName, remotePlayerState.mComponent);
			}
		}
		firstName = true;
		Component line = Component.text(mShardName + ": ").color(NamedTextColor.BLUE);
		for (Component playerComp : shardPlayers.values()) {
			if (!firstName) {
				line = line.append(Component.text(", "));
			}
			line = line.append(playerComp);
			firstName = false;
		}
		audience.sendMessage(line);

		// Remote shards
		for (Map.Entry<String, Map<String, RemotePlayerState>> shardRemotePlayerPairs : mRemotePlayersByShard.entrySet()) {
			lightRow = !lightRow;
			String remoteShardName = shardRemotePlayerPairs.getKey();
			Map<String, RemotePlayerState> remotePlayers = shardRemotePlayerPairs.getValue();

			shardPlayers.clear();
			for (Map.Entry<String, RemotePlayerState> playerEntry : remotePlayers.entrySet()) {
				String playerName = playerEntry.getKey();
				RemotePlayerState remotePlayerState = playerEntry.getValue();
				if (!remotePlayerState.mIsHidden) {
					shardPlayers.put(playerName, remotePlayerState.mComponent);
				}
			}
			firstName = true;
			line = Component.text(remoteShardName + ": ").color(lightRow ? NamedTextColor.BLUE : NamedTextColor.DARK_BLUE);
			for (Component playerComp : shardPlayers.values()) {
				if (!firstName) {
					line = line.append(Component.text(", "));
				}
				line = line.append(playerComp);
				firstName = false;
			}
			audience.sendMessage(line);
		}
	}

	public static void refreshLocalPlayers() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			refreshLocalPlayer(player);
		}
	}

	// Run this on any player to update their displayed name
	public static void refreshLocalPlayer(Player player) {
		mPlugin.getLogger().info("Refreshing local player " + player.getName());
		RemotePlayerState remotePlayerState = new RemotePlayerState(mPlugin, player, true);

		unregisterPlayer(remotePlayerState.mUuid);
		mPlugin.getLogger().info("Registering player " + remotePlayerState.mName);
		mPlayersByUuid.put(remotePlayerState.mUuid, remotePlayerState);
		mPlayersByName.put(remotePlayerState.mName, remotePlayerState);
		if (remotePlayerState.mIsHidden) {
			mVisiblePlayers.remove(remotePlayerState.mUuid);
		} else {
			mVisiblePlayers.add(remotePlayerState.mUuid);
		}

		remotePlayerState.broadcast();
	}

	private static void unregisterPlayer(UUID playerId) {
		@Nullable RemotePlayerState lastRemotePlayerState = mPlayersByUuid.get(playerId);
		if (lastRemotePlayerState != null) {
		    mPlugin.getLogger().info("Unregistering player " + lastRemotePlayerState.mName);
		    String lastLocation = lastRemotePlayerState.mShard;
		    @Nullable Map<String, RemotePlayerState> lastShardRemotePlayers = mRemotePlayersByShard.get(lastLocation);
		    if (lastShardRemotePlayers != null) {
		        lastShardRemotePlayers.remove(lastRemotePlayerState.mName);
		    }
		    mPlayersByUuid.remove(playerId);
		    mPlayersByName.remove(lastRemotePlayerState.mName);
		    mVisiblePlayers.remove(playerId);
		}
	}

	private void remotePlayerChange(JsonObject data) {
		RemotePlayerState remotePlayerState;

		try {
			remotePlayerState = new RemotePlayerState(mPlugin, data);
		} catch (Exception e) {
			mPlugin.getLogger().severe("Got " + REMOTE_PLAYER_CHANNEL + " channel with invalid data");
			mPlugin.getLogger().severe(data.toString());
			mPlugin.getLogger().severe(e.toString());
			return;
		}

		unregisterPlayer(remotePlayerState.mUuid);
		if (remotePlayerState.mIsOnline) {
		    @Nullable Map<String, RemotePlayerState> shardRemotePlayers = mRemotePlayersByShard.get(remotePlayerState.mShard);
		    if (shardRemotePlayers != null) {
		        shardRemotePlayers.put(remotePlayerState.mName, remotePlayerState);
		    }
		    mPlugin.getLogger().info("Registering player " + remotePlayerState.mName);
		    mPlayersByUuid.put(remotePlayerState.mUuid, remotePlayerState);
		    mPlayersByName.put(remotePlayerState.mName, remotePlayerState);
		    if (!remotePlayerState.mIsHidden) {
		        mVisiblePlayers.add(remotePlayerState.mUuid);
		    }
		} else if (!mShardName.equals(remotePlayerState.mShard)) {
			mPlugin.getLogger().info("Detected race condition, triggering refresh on " + remotePlayerState.mName);
			@Nullable Player localPlayer = Bukkit.getPlayer(remotePlayerState.mUuid);
			if (localPlayer != null) {
				refreshLocalPlayer(localPlayer);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void destOnlineEvent(DestOnlineEvent event) {
		String remoteShardName = event.getDest();
		if (mShardName.equals(remoteShardName)) {
			return;
		}
		mPlugin.getLogger().info("Registering shard " + remoteShardName);
		mRemotePlayersByShard.put(remoteShardName, new ConcurrentSkipListMap<>());
	}

	@EventHandler(priority = EventPriority.LOW)
	public void destOfflineEvent(DestOfflineEvent event) throws Exception {
		String remoteShardName = event.getDest();
		@Nullable Map<String, RemotePlayerState> remotePlayers = mRemotePlayersByShard.get(remoteShardName);
		if (remotePlayers == null) {
			return;
		}
		mPlugin.getLogger().info("Unregistering shard " + remoteShardName);
		Map<String, RemotePlayerState> remotePlayersCopy = new ConcurrentSkipListMap<>(remotePlayers);
		for (Map.Entry<String, RemotePlayerState> playerDetails : remotePlayersCopy.entrySet()) {
			RemotePlayerState remotePlayerState = playerDetails.getValue();
			unregisterPlayer(remotePlayerState.mUuid);
		}
		mRemotePlayersByShard.remove(remoteShardName);
	}

	// Player ran a command
	@EventHandler(priority = EventPriority.LOW)
	public void playerCommandPreprocessEvent(PlayerCommandPreprocessEvent event) {
		String command = event.getMessage();

		if (command.startsWith("/team ")
		    || command.contains(" run team ")
		    || command.startsWith("/pv ")
		    || command.contains("vanish")) {
			new BukkitRunnable() {
				@Override
				public void run() {
					refreshLocalPlayers();
				}
			}.runTaskLater(mPlugin, 0);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void playerJoinEvent(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		refreshLocalPlayer(player);
	}

	@EventHandler(priority = EventPriority.LOW)
	public void playerQuitEvent(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		RemotePlayerState remotePlayerState = new RemotePlayerState(mPlugin, player, false);
		unregisterPlayer(remotePlayerState.mUuid);
		remotePlayerState.broadcast();
	}

	@EventHandler(priority = EventPriority.LOW)
	public void networkRelayMessageEvent(NetworkRelayMessageEvent event) throws Exception {
		switch (event.getChannel()) {
		case REMOTE_PLAYER_CHANNEL:
			@Nullable JsonObject data = event.getData();
			if (data == null) {
				mPlugin.getLogger().severe("Got " + REMOTE_PLAYER_CHANNEL + " channel with null data");
				return;
			}
			remotePlayerChange(data);
			break;
		case REFRESH_CHANNEL:
			refreshLocalPlayers();
			break;
		default:
			break;
		}
	}
}
