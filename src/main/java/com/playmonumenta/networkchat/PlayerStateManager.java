package com.playmonumenta.networkchat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers.ChatType;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.event.PlayerSaveEvent;
import com.playmonumenta.redissync.RedisAPI;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerStateManager implements Listener {
	private static final String IDENTIFIER = "NetworkChat";
	private static final String REDIS_PLAYER_EVENT_SETTINGS_KEY = "player_event_settings";

	private static PlayerStateManager INSTANCE = null;
	private static Plugin mPlugin = null;
	private static Map<UUID, PlayerState> mPlayerStates = new HashMap<>();
	private static Map<UUID, PlayerChatHistory> mPlayerChatHistories = new HashMap<>();
	private static MessageVisibility mMessageVisibility = new MessageVisibility();
	private static boolean mIsDefaultChatPlugin = true;

	private PlayerStateManager(Plugin plugin) {
		INSTANCE = this;
		mPlugin = plugin;
		ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin,
		                                                                         ListenerPriority.NORMAL,
		                                                                         PacketType.Play.Server.CHAT) {
			@Override
			public void onPacketSending(PacketEvent event) {
				if (event.getPacketType().equals(PacketType.Play.Server.CHAT)) {
					PacketContainer packet = event.getPacket();
					ChatType chatType = packet.getChatTypes().getValues().get(0);
					if (chatType.equals(ChatType.GAME_INFO)) {
						// Ignore hotbar messages
						return;
					}
					UUID sender = null;
					List<UUID> uuids = packet.getUUIDs().getValues();
					if (uuids.size() == 1) {
						sender = uuids.get(0);
					}
					List<WrappedChatComponent> messageParts = packet.getChatComponents().getValues();
					if (messageParts.size() == 0) {
						return;
					}

					Gson gson = new Gson();
					String messageJsonStr;
					JsonObject messageJson;
					Component messageComponent = null;
					WrappedChatComponent messagePart = messageParts.get(0);
					if (messagePart != null) {
						messageJsonStr = messagePart.getJson();
						messageJson = gson.fromJson(messageJsonStr, JsonObject.class);
						try {
							messageComponent = MessagingUtils.GSON_SERIALIZER.deserializeFromTree(messageJson);
						} catch (JsonParseException e) {
							// This is the fault of some other plugin, with no way to trace it. Silently ignore it.
							return;
						}
					} else {
						List<Object> packetParts = packet.getModifier().getValues();
						for (Object possiblyMessage : packetParts) {
							if (possiblyMessage instanceof Component) {
								messageComponent = (Component) possiblyMessage;
								break;
							}
						}
						if (messageComponent == null) {
							return;
						}
					}

					MessageType messageType;
					if (chatType.equals(ChatType.CHAT)) {
						messageType = MessageType.CHAT;
					} else {
						messageType = MessageType.SYSTEM;
					}
					Message message = Message.createRawMessage(messageType, sender, null, messageComponent);

					PlayerState playerState = mPlayerStates.get(event.getPlayer().getUniqueId());
					if (playerState != null) {
						// A message or two may get lost when a player first joins, and their chat state hasn't loaded yet.
						playerState.receiveExternalMessage(message);
					}
				}
			}
		});
		reload();
	}

	public static PlayerStateManager getInstance() {
		return INSTANCE;
	}

	public static PlayerStateManager getInstance(Plugin plugin) {
		if (INSTANCE == null) {
			INSTANCE = new PlayerStateManager(plugin);
		}
		return INSTANCE;
	}

	public static void reload() {
		RedisAPI.getInstance().async().hget(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_PLAYER_EVENT_SETTINGS_KEY)
			.thenApply(dataStr -> {
			if (dataStr != null) {
				Gson gson = new Gson();
				JsonObject dataJson = gson.fromJson(dataStr, JsonObject.class);
				loadSettings(dataJson);
			}
			return dataStr;
		});
	}

	public static void loadSettings(JsonObject playerEventSettingsJson) {
		mPlugin.getLogger().info("Loading PlayerStateManager settings...");
		mMessageVisibility = MessageVisibility.fromJson(playerEventSettingsJson.getAsJsonObject("message_visibility"));
		mIsDefaultChatPlugin = playerEventSettingsJson.getAsJsonPrimitive("is_default_chat").getAsBoolean();
	}

	public static void saveSettings() {
		mPlugin.getLogger().info("Saving PlayerStateManager settings...");
		JsonObject playerEventSettingsJson = new JsonObject();
		playerEventSettingsJson.add("message_visibility", mMessageVisibility.toJson());
		playerEventSettingsJson.addProperty("is_default_chat", mIsDefaultChatPlugin);

		RedisAPI.getInstance().async().hset(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_PLAYER_EVENT_SETTINGS_KEY, playerEventSettingsJson.toString());

		JsonObject wrappedConfigJson = new JsonObject();
		wrappedConfigJson.add(REDIS_PLAYER_EVENT_SETTINGS_KEY, playerEventSettingsJson);
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE,
			                                             wrappedConfigJson,
			                                             NetworkChatPlugin.getMessageTtl());
		} catch (Exception e) {
			mPlugin.getLogger().severe("Failed to broadcast " + NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE);
		}
	}

	public static MessageVisibility getDefaultMessageVisibility() {
		return mMessageVisibility;
	}

	public static boolean isDefaultChat() {
		return mIsDefaultChatPlugin;
	}

	public static void setDefaultChat(boolean value) {
		mIsDefaultChatPlugin = value;
		saveSettings();
	}

	public static Map<UUID, PlayerState> getPlayerStates() {
		return new HashMap<>(mPlayerStates);
	}

	public static PlayerState getPlayerState(Player player) {
		return getPlayerState(player.getUniqueId());
	}

	public static PlayerState getPlayerState(UUID playerId) {
		return mPlayerStates.get(playerId);
	}

	public static Map<UUID, PlayerChatHistory> getPlayerChatHistories() {
		return new HashMap<>(mPlayerChatHistories);
	}

	public static PlayerChatHistory getPlayerChatHistory(Player player) {
		return mPlayerChatHistories.get(player.getUniqueId());
	}

	public static PlayerChatHistory getPlayerChatHistory(UUID playerId) {
		return mPlayerChatHistories.get(playerId);
	}

	public static PlayerChatHistory getOrCreatePlayerChatHistory(UUID playerId) {
		PlayerChatHistory playerHistory = mPlayerChatHistories.get(playerId);
		if (playerHistory == null) {
			playerHistory = new PlayerChatHistory(playerId);
			mPlayerChatHistories.put(playerId, playerHistory);
		}
		return playerHistory;
	}

	public static boolean isAnyParticipantLocal(Set<UUID> participants) {
		if (participants == null) {
			return true;
		}
		Set<UUID> onlineParticipants = new HashSet<>(mPlayerStates.keySet());
		onlineParticipants.retainAll(participants);
		return !onlineParticipants.isEmpty();
	}

	public static void unregisterChannel(UUID channelId) {
		for (Map.Entry<UUID, PlayerState> playerStateEntry : mPlayerStates.entrySet()) {
			UUID playerId = playerStateEntry.getKey();
			Player player = Bukkit.getPlayer(playerId);
			PlayerState playerState = playerStateEntry.getValue();
			playerState.unregisterChannel(channelId);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void playerJoinEvent(PlayerJoinEvent event) throws Exception {
		Player player = event.getPlayer();
		UUID playerId = player.getUniqueId();

		getOrCreatePlayerChatHistory(playerId);

		// Load player chat state, if it exists.
		JsonObject data = MonumentaRedisSyncAPI.getPlayerPluginData(playerId, IDENTIFIER);
		PlayerState playerState;
		if (data == null) {
			playerState = new PlayerState(player);
			mPlayerStates.put(playerId, playerState);
			mPlugin.getLogger().info("Created new chat state for for player " + player.getName());
		} else {
			try {
				playerState = PlayerState.fromJson(player, data);
				mPlayerStates.put(playerId, playerState);
				mPlugin.getLogger().info("Loaded chat state for player " + player.getName());
			} catch (Exception e) {
				playerState = new PlayerState(player);
				mPlayerStates.put(playerId, playerState);
				mPlugin.getLogger().warning("Player's chat state could not be loaded and was reset " + player.getName());
			}
		}

		for (Channel channel : ChannelManager.getLoadedChannels()) {
			if (!(channel instanceof ChannelInviteOnly)) {
				if (!playerState.hasSeenChannelId(channel.getUniqueId())) {
					if (channel.shouldAutoJoin(playerState)) {
						playerState.joinChannel(channel);
					}
				}
			} else if (!(channel instanceof ChannelWhisper)) {
				ChannelInviteOnly channelInvOnly = (ChannelInviteOnly) channel;
				if (!channelInvOnly.isParticipant(player)) {
					continue;
				}
				if (!playerState.hasSeenChannelId(channel.getUniqueId())) {
					playerState.joinChannel(channel);
				}
			}
		}

		for (UUID channelId : playerState.getWatchedChannelIds()) {
			ChannelManager.loadChannel(channelId, playerState);
		}
		for (UUID channelId : playerState.getUnwatchedChannelIds()) {
			ChannelManager.loadChannel(channelId, playerState);
		}
		for (UUID channelId : playerState.getWhisperChannelIds()) {
			ChannelManager.loadChannel(channelId, playerState);
		}

		// TODO Send login messages here (once implemented)
	}

	/* Whenever player data is saved, also save the local data */
	@EventHandler(priority = EventPriority.MONITOR)
	public void playerSaveEvent(PlayerSaveEvent event) {
		Player player = event.getPlayer();

		PlayerState playerState = mPlayerStates.get(player.getUniqueId());
		if (playerState != null) {
			event.setPluginData(IDENTIFIER, playerState.toJson());
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerQuitEvent(PlayerQuitEvent event) throws Exception {
		Player player = event.getPlayer();
		UUID playerId = player.getUniqueId();

		PlayerState oldState = mPlayerStates.get(playerId);
		mPlayerStates.remove(playerId);
		if (oldState != null) {
			for (UUID channelId : oldState.getWatchedChannelIds()) {
				Channel channel = ChannelManager.getChannel(channelId);
				// This conveniently only unloads channels if they're not in use.
				ChannelManager.unloadChannel(channel);
			}
		}

		// Broadcast the player's current chat history
		PlayerChatHistory oldChatHistory = mPlayerChatHistories.get(playerId);
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(PlayerChatHistory.NETWORK_CHAT_PLAYER_CHAT_HISTORY,
			                                             oldChatHistory.toJson(),
			                                             PlayerChatHistory.MAX_OFFLINE_HISTORY_SECONDS);
		} catch (Exception e) {
			mPlugin.getLogger().severe("Failed to broadcast " + PlayerChatHistory.NETWORK_CHAT_PLAYER_CHAT_HISTORY);
			mPlayerChatHistories.remove(playerId);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void asyncChatEvent(AsyncChatEvent event) throws Exception {
		if (event.isCancelled()) {
			return;
		}
		if (!mIsDefaultChatPlugin) {
			return;
		}

		Player player = event.getPlayer();
		PlayerState playerState = mPlayerStates.get(player.getUniqueId());
		if (playerState == null) {
			player.sendMessage(Component.text("You have no chat state and cannot talk. Please report this bug, then reconnect to the server.", NamedTextColor.RED));
			event.setCancelled(true);
			return;
		}

		Component message = event.message();
		String messageStr = PlainComponentSerializer.plain().serialize(message);

		Channel channel = playerState.getActiveChannel();
		if (channel == null) {
			player.sendMessage(Component.text("You have no active channel. Please set one with /chat and try again.", NamedTextColor.RED));
			event.setCancelled(true);
			return;
		}
		try {
			channel.sendMessage(player, messageStr);
		} catch (Exception ex) {
			MessagingUtils.sendStackTrace(player, ex);
		}
		event.setCancelled(true);
	}

	// This should only happen when a player logs out on another shard on the network.
	// If they log in here, their chat history will be loaded.
	// If they log in elsewhere, or don't log back in, the history is discarded after a delay.
	public void handleRemotePlayerChatHistoryMessage(JsonObject data) {
		JsonElement playerIdJson = data.get("playerId");
		if (playerIdJson != null) {
			try {
				final UUID playerId = UUID.fromString(playerIdJson.getAsString());

				getOrCreatePlayerChatHistory(playerId).updateFromJson(data);
				new BukkitRunnable() {
					@Override
					public void run() {
						OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
						if (!offlinePlayer.isOnline()) {
							if (mPlayerChatHistories.containsKey(playerId)) {
								mPlayerChatHistories.remove(playerId);
							}
				        }
					}
				}.runTaskLater(mPlugin, 20 * PlayerChatHistory.MAX_OFFLINE_HISTORY_SECONDS);
			} catch (Exception e) {
				mPlugin.getLogger().severe("Got " + PlayerChatHistory.NETWORK_CHAT_PLAYER_CHAT_HISTORY + " with invalid data");
				return;
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void networkRelayMessageEvent(NetworkRelayMessageEvent event) {
		JsonObject data;
		switch (event.getChannel()) {
		case NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE:
			data = event.getData();
			if (data == null) {
				mPlugin.getLogger().severe("Got " + NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE + " with null data");
				return;
			}
			JsonObject playerEventSettingsJson = data.getAsJsonObject(REDIS_PLAYER_EVENT_SETTINGS_KEY);
			if (playerEventSettingsJson != null) {
				loadSettings(playerEventSettingsJson);
			}
			break;
		case PlayerChatHistory.NETWORK_CHAT_PLAYER_CHAT_HISTORY:
			data = event.getData();
			if (data == null) {
				mPlugin.getLogger().severe("Got " + PlayerChatHistory.NETWORK_CHAT_PLAYER_CHAT_HISTORY + " with null data");
				return;
			}
			handleRemotePlayerChatHistoryMessage(data);
			break;
		default:
			break;
		}
	}
}
