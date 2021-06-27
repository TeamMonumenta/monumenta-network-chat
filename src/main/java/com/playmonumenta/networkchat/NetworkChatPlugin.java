package com.playmonumenta.networkchat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;
import com.playmonumenta.redissync.RedisAPI;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class NetworkChatPlugin extends JavaPlugin implements Listener {
	// Config is partially handled in other classes, such as ChannelManager
	public static final String NETWORK_CHAT_CONFIG_UPDATE = "com.playmonumenta.networkchat.config_update";
	protected static final String REDIS_CONFIG_PATH = "networkchat:config";
	private static final String REDIS_MESSAGE_COLORS_KEY = "message_colors";
	private static final String REDIS_MESSAGE_FORMATS_KEY = "message_formats";

	private static NetworkChatPlugin INSTANCE = null;
	public static Map<String, TextColor> mDefaultMessageColors = new ConcurrentSkipListMap<>();
	public static Map<String, String> mDefaultMessageFormats = new ConcurrentSkipListMap<>();
	public static Map<String, TextColor> mMessageColors = new ConcurrentSkipListMap<>();
	public static Map<String, String> mMessageFormats = new ConcurrentSkipListMap<>();
	public static ChannelManager mChannelManager = null;
	public static MessageManager mMessageManager = null;
	public static PlayerStateManager mPlayerStateManager = null;
	public static RemotePlayerManager mRemotePlayerManager = null;

	@Override
	public void onLoad() {
		// TODO Move these formatting options to a defaults structure, then copy to the final mappings
		mDefaultMessageFormats.put("player", "<insert:%player_name%>"
			+ "<click:suggest_command:/tell %player_name% >"
			+ "<hover:show_entity:'minecraft:player':%player_uuid%:%player_name%>"
			+ "<team_color><team_prefix>%player_name%<team_suffix>"
			+ "</hover></click></insert>");

		mDefaultMessageColors.put(ChannelAnnouncement.CHANNEL_CLASS_ID, NamedTextColor.RED);
		mDefaultMessageFormats.put(ChannelAnnouncement.CHANNEL_CLASS_ID, "<gray><hover:show_text:\"<channel_color>Announcement Channel\">\\<<channel_color><channel_name><gray>></hover>");
		mDefaultMessageColors.put(ChannelGlobal.CHANNEL_CLASS_ID, NamedTextColor.WHITE);
		mDefaultMessageFormats.put(ChannelGlobal.CHANNEL_CLASS_ID, "<gray><hover:show_text:\"<channel_color>Global Channel\">\\<<channel_color><channel_name><gray>></hover> <white><sender> <gray>»");
		mDefaultMessageColors.put(ChannelLocal.CHANNEL_CLASS_ID, NamedTextColor.YELLOW);
		mDefaultMessageFormats.put(ChannelLocal.CHANNEL_CLASS_ID, "<gray><hover:show_text:\"<channel_color>Local Channel\">\\<<channel_color><channel_name><gray>></hover> <white><sender> <gray>»");
		mDefaultMessageColors.put(ChannelParty.CHANNEL_CLASS_ID, NamedTextColor.LIGHT_PURPLE);
		mDefaultMessageFormats.put(ChannelParty.CHANNEL_CLASS_ID, "<gray><hover:show_text:\"<channel_color>Party Channel\">\\<<channel_color><channel_name><gray>></hover> <white><sender> <gray>»");
		mDefaultMessageColors.put(ChannelWhisper.CHANNEL_CLASS_ID, NamedTextColor.GRAY);
		mDefaultMessageFormats.put(ChannelWhisper.CHANNEL_CLASS_ID, "<channel_color><sender> whispers to <receiver> <gray>»");

		mMessageColors.putAll(mDefaultMessageColors);
		mMessageFormats.putAll(mDefaultMessageFormats);
		RemotePlayerManager.refreshLocalPlayers();

		ChatCommand.register();
	}

	@Override
	public void onEnable() {
		INSTANCE = this;

		/* Check for Placeholder API */
		if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			getLogger().log(Level.SEVERE, "Could not find PlaceholderAPI! This plugin is required.");
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		mChannelManager = ChannelManager.getInstance(this);
		mMessageManager = MessageManager.getInstance(this);
		mPlayerStateManager = PlayerStateManager.getInstance(this);
		mRemotePlayerManager = RemotePlayerManager.getInstance(this);

		getServer().getPluginManager().registerEvents(mChannelManager, this);
		getServer().getPluginManager().registerEvents(mMessageManager, this);
		getServer().getPluginManager().registerEvents(mPlayerStateManager, this);
		getServer().getPluginManager().registerEvents(mRemotePlayerManager, this);
		getServer().getPluginManager().registerEvents(this, this);

		RedisAPI.getInstance().async().hget(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_MESSAGE_COLORS_KEY)
			.thenApply(dataStr -> {
			if (dataStr != null) {
				Gson gson = new Gson();
				JsonObject dataJson = gson.fromJson(dataStr, JsonObject.class);
				colorsFromJson(dataJson);
			}
			return dataStr;
		});

		RedisAPI.getInstance().async().hget(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_MESSAGE_FORMATS_KEY)
			.thenApply(dataStr -> {
			if (dataStr != null) {
				Gson gson = new Gson();
				JsonObject dataJson = gson.fromJson(dataStr, JsonObject.class);
				for (Map.Entry<String, JsonElement> entry : dataJson.entrySet()) {
					String id = entry.getKey();
					JsonElement element = entry.getValue();
					if (!element.isJsonPrimitive()
					  || !element.getAsJsonPrimitive().isString()) {
						continue;
					}
					String value = element.getAsString();
					mMessageFormats.put(id, value);
				}
			}
			return dataStr;
		});
	}

	@Override
	public void onDisable() {
		INSTANCE = null;
		getServer().getScheduler().cancelTasks(this);
	}

	public static NetworkChatPlugin getInstance() {
		return INSTANCE;
	}

	public static void colorsFromJson(JsonObject dataJson) {
		for (Map.Entry<String, JsonElement> entry : dataJson.entrySet()) {
			String id = entry.getKey();
			JsonElement element = entry.getValue();
			if (!element.isJsonPrimitive()
			  || !element.getAsJsonPrimitive().isString()) {
				continue;
			}
			String value = element.getAsString();
			TextColor color = MessagingUtils.colorFromString(value);
			if (color != null) {
				mMessageColors.put(id, color);
			}
		}
	}

	public static void saveColors() {
		JsonObject dataJson = new JsonObject();
		for (Map.Entry<String, TextColor> entry : mMessageColors.entrySet()) {
			String id = entry.getKey();
			TextColor color = entry.getValue();
			dataJson.addProperty(id, MessagingUtils.colorToString(color));
		}

		RedisAPI.getInstance().async().hset(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_MESSAGE_COLORS_KEY, dataJson.toString());

		JsonObject wrappedConfigJson = new JsonObject();
		wrappedConfigJson.add(REDIS_MESSAGE_COLORS_KEY, dataJson);
		try {
			NetworkRelayAPI.sendBroadcastMessage(NETWORK_CHAT_CONFIG_UPDATE, wrappedConfigJson);
		} catch (Exception e) {
			INSTANCE.getLogger().severe("Failed to broadcast " + NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE);
		}
	}

	public static TextColor messageColor(String id) {
		return mMessageColors.get(id);
	}

	public static void messageColor(String id, TextColor value) {
		mMessageColors.put(id, value);
		saveColors();
	}

	public static void formatsFromJson(JsonObject dataJson) {
		for (Map.Entry<String, JsonElement> entry : dataJson.entrySet()) {
			String id = entry.getKey();
			JsonElement element = entry.getValue();
			if (!element.isJsonPrimitive()
			  || !element.getAsJsonPrimitive().isString()) {
				continue;
			}
			String value = element.getAsString();
			mMessageFormats.put(id, value);
		}
	}

	public static void saveFormats() {
		JsonObject dataJson = new JsonObject();
		for (Map.Entry<String, String> entry : mMessageFormats.entrySet()) {
			String id = entry.getKey();
			String format = entry.getValue();
			dataJson.addProperty(id, format);
		}

		RedisAPI.getInstance().async().hset(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_MESSAGE_FORMATS_KEY, dataJson.toString());

		JsonObject wrappedConfigJson = new JsonObject();
		wrappedConfigJson.add(REDIS_MESSAGE_FORMATS_KEY, dataJson);
		try {
			NetworkRelayAPI.sendBroadcastMessage(NETWORK_CHAT_CONFIG_UPDATE, wrappedConfigJson);
		} catch (Exception e) {
			INSTANCE.getLogger().severe("Failed to broadcast " + NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE);
		}
	}

	public static String messageFormat(String id) {
		return mMessageFormats.get(id);
	}

	public static void messageFormat(String id, String value) {
		mMessageFormats.put(id, value);
		saveFormats();
		if (id.equals("player")) {
			RemotePlayerManager.refreshLocalPlayers();
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void networkRelayMessageEvent(NetworkRelayMessageEvent event) {
		JsonObject data;
		switch (event.getChannel()) {
		case NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE:
			data = event.getData();
			if (data == null) {
				getLogger().severe("Got " + NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE + " channel with null data");
				return;
			}

			JsonObject messageColorsJson = data.getAsJsonObject(REDIS_MESSAGE_COLORS_KEY);
			if (messageColorsJson != null) {
				colorsFromJson(messageColorsJson);
			}

			JsonObject messageFormatsJson = data.getAsJsonObject(REDIS_MESSAGE_FORMATS_KEY);
			if (messageFormatsJson != null) {
				formatsFromJson(messageFormatsJson);
			}

			break;
		default:
			break;
		}
	}
}
