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
	public static final String REMOTE_PLAYER_CHANNEL = "com.playmonumenta.networkchat.RemotePlayerManager.remoteplayer";
	public static final String REFRESH_CHANNEL = "com.playmonumenta.networkchat.RemotePlayerManager.refresh";

	private static RemotePlayerManager INSTANCE = null;
	private static Plugin mPlugin = null;
	private static String mShardName = null;
	private static Map<String, Map<String, UUID>> mRemotePlayers = new ConcurrentSkipListMap<>();
	private static Map<String, UUID> mPlayerIds = new ConcurrentSkipListMap<>();
	private static Map<UUID, String> mPlayerNames = new ConcurrentSkipListMap<>();
	private static Map<UUID, String> mPlayerLocations = new ConcurrentSkipListMap<>();
	private static Map<UUID, Component> mPlayerComponents = new ConcurrentSkipListMap<>();
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
				mRemotePlayers.put(shard, new ConcurrentSkipListMap<>());
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

	public static RemotePlayerManager getInstance() {
		return INSTANCE;
	}

	public static RemotePlayerManager getInstance(Plugin plugin) {
		if (INSTANCE == null) {
			INSTANCE = new RemotePlayerManager(plugin);
		}
		return INSTANCE;
	}

	public static String getShardName() {
		return mShardName;
	}

	public static Set<String> onlinePlayerNames() {
		return new ConcurrentSkipListSet<>(mPlayerIds.keySet());
	}

	public static boolean isPlayerOnline(UUID playerId) {
		return mPlayerNames.containsKey(playerId);
	}

	public static boolean isPlayerOnline(String playerName) {
		return mPlayerIds.containsKey(playerName);
	}

	public static Set<String> visiblePlayerNames() {
		Set<String> results = new ConcurrentSkipListSet<>();
		for (UUID playerId : mVisiblePlayers) {
			results.add(getPlayerName(playerId));
		}
		return results;
	}

	private static boolean isLocalPlayerVisible(Player player) {
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
		return mVisiblePlayers.contains(getPlayerId(playerName));
	}

	public static String getPlayerName(UUID playerUuid) {
		return mPlayerNames.get(playerUuid);
	}

	public static Component getPlayerComponent(UUID playerUuid) {
		if (!isPlayerVisible(playerUuid)) {
			// Note: offline players are not visible
			String playerName = MonumentaRedisSyncAPI.cachedUuidToName(playerUuid);
			if (playerName != null) {
				return Component.text(playerName, NamedTextColor.RED)
					.hoverEvent(Component.text("Offline", NamedTextColor.RED));
			}
		}
		Component result = mPlayerComponents.get(playerUuid);
		if (result == null) {
			return Component.empty();
		}
		return result;
	}

	public static UUID getPlayerId(String playerName) {
		return mPlayerIds.get(playerName);
	}

	public static String getPlayerShard(UUID playerId) {
		return mPlayerLocations.get(playerId);
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
			if (isPlayerVisible(player)) {
				shardPlayers.put(player.getName(), mPlayerComponents.get(player.getUniqueId()));
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
		for (Map.Entry<String, Map<String, UUID>> shardRemotePlayerPairs : mRemotePlayers.entrySet()) {
			lightRow = !lightRow;
			String remoteShardName = shardRemotePlayerPairs.getKey();
			Map<String, UUID> remotePlayers = shardRemotePlayerPairs.getValue();

			shardPlayers.clear();
			for (Map.Entry<String, UUID> playerEntry : remotePlayers.entrySet()) {
				String playerName = playerEntry.getKey();
				UUID playerUuid = playerEntry.getValue();
				if (isPlayerVisible(playerUuid)) {
					shardPlayers.put(playerName, mPlayerComponents.get(playerUuid));
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
		String playerName = player.getName();
		UUID playerUuid = player.getUniqueId();
		Component playerComponent = MessagingUtils.playerComponent(player);
		boolean isHidden = !isLocalPlayerVisible(player);

		if (isHidden) {
			mVisiblePlayers.remove(playerUuid);
		} else {
			mVisiblePlayers.add(playerUuid);
		}
		mPlayerIds.put(playerName, playerUuid);
		mPlayerNames.put(playerUuid, playerName);
		mPlayerLocations.put(playerUuid, mShardName);
		mPlayerComponents.put(playerUuid, playerComponent);

		JsonObject remotePlayerData = new JsonObject();
		remotePlayerData.addProperty("isHidden", isHidden);
		remotePlayerData.addProperty("isOnline", true);
		remotePlayerData.addProperty("shard", mShardName);
		remotePlayerData.addProperty("playerName", playerName);
		remotePlayerData.addProperty("playerUuid", playerUuid.toString());
		remotePlayerData.add("playerComponent", MessagingUtils.toJson(playerComponent));

		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(REMOTE_PLAYER_CHANNEL,
			                                             remotePlayerData,
			                                             NetworkChatPlugin.getMessageTtl());
		} catch (Exception e) {
			mPlugin.getLogger().severe("Failed to broadcast " + REMOTE_PLAYER_CHANNEL);
		}
	}

	private void remotePlayerChange(JsonObject data) {
		boolean isHidden = false;
		boolean isOnline = false;
		String remoteShardName;
		String playerName;
		UUID playerUuid;
		Component playerComponent;
		String lastLocation = null;

		try {
			isHidden = data.get("isHidden").getAsBoolean();
			isOnline = data.get("isOnline").getAsBoolean();
			remoteShardName = data.get("shard").getAsString();
			playerName = data.get("playerName").getAsString();
			playerUuid = UUID.fromString(data.get("playerUuid").getAsString());
			playerComponent = MessagingUtils.fromJson(data.get("playerComponent"));
		} catch (Exception e) {
			mPlugin.getLogger().severe("Got " + REMOTE_PLAYER_CHANNEL + " channel with invalid data");
			mPlugin.getLogger().severe(data.toString());
			mPlugin.getLogger().severe(e.toString());
			return;
		}
		lastLocation = mPlayerLocations.get(playerUuid);

		if (mShardName.equals(remoteShardName)) {
			return;
		}
		Map<String, UUID> remotePlayers = mRemotePlayers.get(remoteShardName);

		if (isOnline) {
			if (isHidden) {
				mVisiblePlayers.remove(playerUuid);
			} else {
				mVisiblePlayers.add(playerUuid);
			}
			remotePlayers.put(playerName, playerUuid);
			mPlayerLocations.put(playerUuid, remoteShardName);
			mPlayerIds.put(playerName, playerUuid);
			mPlayerNames.put(playerUuid, playerName);
			mPlayerComponents.put(playerUuid, playerComponent);
		} else if (remoteShardName.equals(lastLocation)) {
			mVisiblePlayers.remove(playerUuid);
			remotePlayers.remove(playerName);
			mPlayerLocations.remove(playerUuid);
			mPlayerIds.remove(playerName);
			mPlayerNames.remove(playerUuid);
			mPlayerComponents.remove(playerUuid);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void destOnlineEvent(DestOnlineEvent event) {
		String remoteShardName = event.getDest();
		if (mShardName.equals(remoteShardName)) {
			return;
		}
		mRemotePlayers.put(remoteShardName, new ConcurrentSkipListMap<>());
	}

	@EventHandler(priority = EventPriority.LOW)
	public void destOfflineEvent(DestOfflineEvent event) throws Exception {
		String remoteShardName = event.getDest();
		Map<String, UUID> remotePlayers = mRemotePlayers.get(remoteShardName);
		if (remotePlayers == null) {
			return;
		}
		mRemotePlayers.remove(remoteShardName);
		for (Map.Entry<String, UUID> playerDetails : remotePlayers.entrySet()) {
			mPlayerIds.remove(playerDetails.getKey());
			mPlayerNames.remove(playerDetails.getValue());
		}
	}

	// Player ran a command
	@EventHandler(priority = EventPriority.LOW)
	public void playerCommandPreprocessEvent(PlayerCommandPreprocessEvent event) {
		String command = event.getMessage().substring(1);

		if (command.startsWith("team ")
		    || command.startsWith("pv ")
		    || command.contains("vanish")) {
			new BukkitRunnable() {
				@Override
				public void run() {
					for (Player player : Bukkit.getOnlinePlayers()) {
						refreshLocalPlayer(player);
					}
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
		String playerName = player.getName();
		UUID playerUuid = player.getUniqueId();
		Component playerComponent = MessagingUtils.playerComponent(player);
		boolean isHidden = !isPlayerVisible(playerUuid);

		mVisiblePlayers.remove(playerUuid);
		mPlayerLocations.remove(playerUuid);
		mPlayerIds.remove(playerName);
		mPlayerNames.remove(playerUuid);
		mPlayerComponents.remove(playerUuid);

		JsonObject remotePlayerData = new JsonObject();
		remotePlayerData.addProperty("isHidden", isHidden);
		remotePlayerData.addProperty("isOnline", false);
		remotePlayerData.addProperty("shard", mShardName);
		remotePlayerData.addProperty("playerName", playerName);
		remotePlayerData.addProperty("playerUuid", playerUuid.toString());
		remotePlayerData.add("playerComponent", MessagingUtils.toJson(playerComponent));

		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(REMOTE_PLAYER_CHANNEL,
			                                             remotePlayerData,
			                                             NetworkChatPlugin.getMessageTtl());
		} catch (Exception e) {
			mPlugin.getLogger().severe("Failed to broadcast " + REMOTE_PLAYER_CHANNEL);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void networkRelayMessageEvent(NetworkRelayMessageEvent event) throws Exception {
		switch (event.getChannel()) {
		case REMOTE_PLAYER_CHANNEL:
			JsonObject data = event.getData();
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
