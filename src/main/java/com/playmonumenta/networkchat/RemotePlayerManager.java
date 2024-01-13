package com.playmonumenta.networkchat;

import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.networkrelay.DestOfflineEvent;
import com.playmonumenta.networkrelay.DestOnlineEvent;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

public class RemotePlayerManager implements Listener {
	static class RemotePlayer {
		public final UUID mUuid;
		public final String mName;
		public final Component mComponent;
		public final boolean mIsHidden;
		public final boolean mIsOnline;
		public final String mShard;

		public RemotePlayer(Player player, boolean isOnline) {
			mUuid = player.getUniqueId();
			mName = player.getName();
			mComponent = MessagingUtils.playerComponent(player);
			mIsHidden = !internalPlayerVisibleTest(player);
			mIsOnline = isOnline;
			mShard = getShardName();

			MMLog.fine("Created RemotePlayerState for " + mName + " from " + mShard + ": " + (mIsOnline ? "online" : "offline"));
		}

		public RemotePlayer(JsonObject remoteData) {
			mUuid = UUID.fromString(remoteData.get("playerUuid").getAsString());
			mName = remoteData.get("playerName").getAsString();
			mComponent = MessagingUtils.fromJson(remoteData.get("playerComponent"));
			mIsHidden = remoteData.get("isHidden").getAsBoolean();
			mIsOnline = remoteData.get("isOnline").getAsBoolean();
			mShard = remoteData.get("shard").getAsString();

			MMLog.fine("Received RemotePlayerState for " + mName + " from " + mShard + ": " + (mIsOnline ? "online" : "offline"));
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
				NetworkRelayAPI.sendExpiringBroadcastMessage(REMOTE_PLAYER_UPDATE_CHANNEL,
					remotePlayerData,
					NetworkChatPlugin.getMessageTtl());
			} catch (Exception e) {
				MMLog.severe("Failed to broadcast " + REMOTE_PLAYER_UPDATE_CHANNEL);
			}
		}
	}

	public static final String REMOTE_PLAYER_UPDATE_CHANNEL = "com.playmonumenta.networkchat.RemotePlayerManager.remoteplayer";
	public static final String REMOTE_PLAYER_REFRESH_CHANNEL = "com.playmonumenta.networkchat.RemotePlayerManager.refresh";
	public static final ArgumentSuggestions SUGGESTIONS_VISIBLE_PLAYER_NAMES = ArgumentSuggestions.strings(info ->
		visiblePlayerNames().toArray(new String[0]));

	private static @MonotonicNonNull RemotePlayerManager INSTANCE = null;
	private static final Map<String, Map<String, RemotePlayer>> mRemotePlayerShardMapped = new ConcurrentSkipListMap<>();
	private static final Map<UUID, RemotePlayer> mRemotePlayersByUuid = new ConcurrentSkipListMap<>();
	private static final Map<String, RemotePlayer> mRemotePlayersByName = new ConcurrentSkipListMap<>();
	private static final Set<UUID> mVisiblePlayers = new ConcurrentSkipListSet<>();

	private RemotePlayerManager() {
		INSTANCE = this;
		String lShard = getShardName();
		try {
			for (String shard : NetworkRelayAPI.getOnlineShardNames()) {
				if (lShard.equals(shard)) {
					continue;
				}
				MMLog.fine("Registering shard " + shard);
				mRemotePlayerShardMapped.put(shard, new ConcurrentSkipListMap<>());
			}
		} catch (Exception e) {
			MMLog.severe("Failed to get remote shard names");
			throw new RuntimeException("Failed to get remote shard names");
		}
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(REMOTE_PLAYER_REFRESH_CHANNEL,
				new JsonObject(),
				NetworkChatPlugin.getMessageTtl());
		} catch (Exception e) {
			MMLog.severe("Failed to broadcast " + REMOTE_PLAYER_REFRESH_CHANNEL);
		}
	}

	public static RemotePlayerManager getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new RemotePlayerManager();
		}
		return INSTANCE;
	}

	public static String getShardName() {
		@Nullable String shardName = null;
		try {
			shardName = NetworkRelayAPI.getShardName();
		} catch (Exception e) {
			MMLog.severe("Failed to get shard name");
		}
		if (shardName == null) {
			throw new RuntimeException("Got null shard name");
		}
		return shardName;
	}

	public static Set<String> onlinePlayerNames() {
		return new ConcurrentSkipListSet<>(mRemotePlayersByName.keySet());
	}

	public static boolean isPlayerOnline(UUID playerUuid) {
		return mRemotePlayersByUuid.containsKey(playerUuid);
	}

	public static boolean isPlayerOnline(String playerName) {
		return mRemotePlayersByName.containsKey(playerName);
	}

	public static Set<String> visiblePlayerNames() {
		Set<String> results = new ConcurrentSkipListSet<>();
		for (UUID playerUuid : mVisiblePlayers) {
			results.add(getPlayerName(playerUuid));
		}
		return results;
	}

	private static boolean internalPlayerVisibleTest(Player player) {
		for (MetadataValue meta : player.getMetadata("vanished")) {
			if (meta.asBoolean()) {
				return false;
			}
		}
		return true;
	}

	public static boolean isPlayerVisible(Player player) {
		boolean cachedResult = isPlayerVisible(player.getUniqueId());
		boolean currentResult = internalPlayerVisibleTest(player);
		if (cachedResult ^ currentResult) {
			refreshLocalPlayer(player);
		}
		return currentResult;
	}

	public static boolean isPlayerVisible(UUID playerUuid) {
		return mVisiblePlayers.contains(playerUuid);
	}

	public static boolean isPlayerVisible(String playerName) {
		@Nullable UUID playerUuid = getPlayerUuid(playerName);
		if (playerUuid == null) {
			return false;
		}
		return isPlayerVisible(playerUuid);
	}

	public static @Nullable String getPlayerName(UUID playerUuid) {
		@Nullable RemotePlayer remotePlayer = mRemotePlayersByUuid.get(playerUuid);
		if (remotePlayer == null) {
			return null;
		}
		return remotePlayer.mName;
	}

	public static Component getPlayerComponent(UUID playerUuid) {
		@Nullable RemotePlayer remotePlayer = mRemotePlayersByUuid.get(playerUuid);
		if (remotePlayer == null || remotePlayer.mIsHidden) {
			// Note: offline players are not visible
			@Nullable String playerName = MonumentaRedisSyncAPI.cachedUuidToName(playerUuid);
			if (playerName != null) {
				return Component.text(playerName, NamedTextColor.RED)
					.hoverEvent(Component.text("Offline", NamedTextColor.RED));
			}
			return Component.empty();
		}
		return remotePlayer.mComponent;
	}

	public static @Nullable UUID getPlayerUuid(String playerName) {
		return MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
	}

	public static @Nullable String getPlayerShard(UUID playerUuid) {
		@Nullable RemotePlayer remotePlayer = mRemotePlayersByUuid.get(playerUuid);
		if (remotePlayer == null) {
			return null;
		}
		return remotePlayer.mShard;
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
			@Nullable RemotePlayer remotePlayer = mRemotePlayersByUuid.get(player.getUniqueId());
			if (remotePlayer != null && !remotePlayer.mIsHidden) {
				shardPlayers.put(remotePlayer.mName, remotePlayer.mComponent);
			}
		}
		firstName = true;
		Component line = Component.text(getShardName() + ": ").color(NamedTextColor.BLUE);
		for (Component playerComp : shardPlayers.values()) {
			if (!firstName) {
				line = line.append(Component.text(", "));
			}
			line = line.append(playerComp);
			firstName = false;
		}
		audience.sendMessage(line);

		// Remote shards
		for (Map.Entry<String, Map<String, RemotePlayer>> shardRemotePlayerPairs : mRemotePlayerShardMapped.entrySet()) {
			lightRow = !lightRow;
			String remoteShardName = shardRemotePlayerPairs.getKey();
			Map<String, RemotePlayer> remotePlayers = shardRemotePlayerPairs.getValue();

			shardPlayers.clear();
			for (Map.Entry<String, RemotePlayer> playerEntry : remotePlayers.entrySet()) {
				String playerName = playerEntry.getKey();
				RemotePlayer remotePlayer = playerEntry.getValue();
				if (!remotePlayer.mIsHidden) {
					shardPlayers.put(playerName, remotePlayer.mComponent);
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
		MMLog.fine("Refreshing local player " + player.getName());
		RemotePlayer remotePlayer = new RemotePlayer(player, true);

		unregisterPlayer(remotePlayer.mUuid);
		MMLog.fine("Registering player " + remotePlayer.mName);
		mRemotePlayersByUuid.put(remotePlayer.mUuid, remotePlayer);
		mRemotePlayersByName.put(remotePlayer.mName, remotePlayer);
		if (remotePlayer.mIsHidden) {
			mVisiblePlayers.remove(remotePlayer.mUuid);
		} else {
			mVisiblePlayers.add(remotePlayer.mUuid);
		}

		remotePlayer.broadcast();
	}

	private static void unregisterPlayer(UUID playerUuid) {
		@Nullable RemotePlayer lastPlayerState = mRemotePlayersByUuid.get(playerUuid);
		if (lastPlayerState != null) {
			MMLog.fine("Unregistering player " + lastPlayerState.mName);
			String lastLoc = lastPlayerState.mShard;
			@Nullable Map<String, RemotePlayer> lastShardRemotePlayers = mRemotePlayerShardMapped.get(lastLoc);
			if (lastShardRemotePlayers != null) {
				lastShardRemotePlayers.remove(lastPlayerState.mName);
			}
			mRemotePlayersByUuid.remove(playerUuid);
			mRemotePlayersByName.remove(lastPlayerState.mName);
			mVisiblePlayers.remove(playerUuid);
		}
	}

	private void remotePlayerChange(JsonObject data) {
		RemotePlayer remotePlayer;

		try {
			remotePlayer = new RemotePlayer(data);
		} catch (Exception e) {
			MMLog.severe("Got " + REMOTE_PLAYER_UPDATE_CHANNEL + " channel with invalid data");
			MMLog.severe(data.toString());
			MMLog.severe(e.toString());
			return;
		}

		unregisterPlayer(remotePlayer.mUuid);
		if (remotePlayer.mIsOnline) {
			@Nullable Map<String, RemotePlayer> shardRemotePlayers = mRemotePlayerShardMapped.get(remotePlayer.mShard);
			if (shardRemotePlayers != null) {
				shardRemotePlayers.put(remotePlayer.mName, remotePlayer);
			}
			MMLog.fine("Registering player " + remotePlayer.mName);
			mRemotePlayersByUuid.put(remotePlayer.mUuid, remotePlayer);
			mRemotePlayersByName.put(remotePlayer.mName, remotePlayer);
			if (!remotePlayer.mIsHidden) {
				mVisiblePlayers.add(remotePlayer.mUuid);
			}
		} else if (!getShardName().equals(remotePlayer.mShard)) {
			MMLog.fine("Detected race condition, triggering refresh on " + remotePlayer.mName);
			@Nullable Player localPlayer = Bukkit.getPlayer(remotePlayer.mUuid);
			if (localPlayer != null) {
				refreshLocalPlayer(localPlayer);
			}
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void destOnlineEvent(DestOnlineEvent event) {
		String remoteShardName = event.getDest();
		if (getShardName().equals(remoteShardName)) {
			return;
		}
		MMLog.fine("Registering shard " + remoteShardName);
		mRemotePlayerShardMapped.put(remoteShardName, new ConcurrentSkipListMap<>());
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void destOfflineEvent(DestOfflineEvent event) {
		String remoteShardName = event.getDest();
		@Nullable Map<String, RemotePlayer> remotePlayers = mRemotePlayerShardMapped.get(remoteShardName);
		if (remotePlayers == null) {
			return;
		}
		MMLog.fine("Unregistering shard " + remoteShardName);
		Map<String, RemotePlayer> remotePlayersCopy = new ConcurrentSkipListMap<>(remotePlayers);
		for (Map.Entry<String, RemotePlayer> playerEntry : remotePlayersCopy.entrySet()) {
			RemotePlayer remotePlayer = playerEntry.getValue();
			unregisterPlayer(remotePlayer.mUuid);
		}
		mRemotePlayerShardMapped.remove(remoteShardName);
	}

	// Player ran a command
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void playerCommandPreprocessEvent(PlayerCommandPreprocessEvent event) {
		String command = event.getMessage();

		if (command.startsWith("/team ")
			|| command.contains(" run team ")
			|| command.startsWith("/pv ")
			|| command.equals("/pv")
			|| command.contains("vanish")) {
			new BukkitRunnable() {
				@Override
				public void run() {
					refreshLocalPlayers();
				}
			}.runTaskLater(NetworkChatPlugin.getInstance(), 0);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void playerJoinEvent(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		refreshLocalPlayer(player);
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void playerQuitEvent(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		RemotePlayer remotePlayer = new RemotePlayer(player, false);
		unregisterPlayer(remotePlayer.mUuid);
		remotePlayer.broadcast();
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void networkRelayMessageEvent(NetworkRelayMessageEvent event) {
		switch (event.getChannel()) {
			case REMOTE_PLAYER_UPDATE_CHANNEL -> {
				@Nullable JsonObject data = event.getData();
				if (data == null) {
					MMLog.severe("Got " + REMOTE_PLAYER_UPDATE_CHANNEL + " channel with null data");
					return;
				}
				remotePlayerChange(data);
			}
			case REMOTE_PLAYER_REFRESH_CHANNEL -> refreshLocalPlayers();
			default -> {
			}
		}
	}
}
