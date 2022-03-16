package com.playmonumenta.networkchat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;
import com.playmonumenta.redissync.RedisAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.zip.ZipFile;
import javax.annotation.Nullable;
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
	private static final String REDIS_CHAT_FILTERS_KEY = "chat_filters";

	private static @Nullable NetworkChatPlugin INSTANCE = null;
	private static final Map<String, TextColor> mDefaultMessageColors = new ConcurrentSkipListMap<>();
	private static final Map<String, String> mDefaultMessageFormats = new ConcurrentSkipListMap<>();
	private static final Map<String, TextColor> mMessageColors = new ConcurrentSkipListMap<>();
	private static final Map<String, String> mMessageFormats = new ConcurrentSkipListMap<>();
	private static ChatFilter mGlobalChatFilter = new ChatFilter();
	private static @Nullable ChannelManager mChannelManager = null;
	private static @Nullable MessageManager mMessageManager = null;
	private static @Nullable PlayerStateManager mPlayerStateManager = null;
	private static @Nullable RemotePlayerManager mRemotePlayerManager = null;

	@Override
	public void onLoad() {
		NetworkChatProperties.load(this, null);

		mDefaultMessageFormats.put("player", "<insert:%player_name%>"
			+ "<click:suggest_command:/tell %player_name% >"
			+ "<hover:show_entity:'minecraft:player':%player_uuid%:%player_name%>"
			+ "<team_color><team_prefix>%player_name%<team_suffix>"
			+ "</hover></click></insert>");

		mDefaultMessageFormats.put("entity", "<insert:<entity_uuid>>"
			+ "<hover:show_entity:'<entity_type>':<entity_uuid>:<entity_name>>"
			+ "<team_color><team_prefix><entity_name><team_suffix>"
			+ "</hover></insert>");

		mDefaultMessageFormats.put("sender", "<sender_name>");

		mDefaultMessageColors.put(ChannelAnnouncement.CHANNEL_CLASS_ID, NamedTextColor.RED);
		mDefaultMessageFormats.put(ChannelAnnouncement.CHANNEL_CLASS_ID, "<gray><click:run_command:\"<message_gui_cmd>\"><hover:show_text:\"<channel_color>Announcement Channel\nClick for GUI\">\\<<channel_color><channel_name><gray>></hover></click>");
		mDefaultMessageColors.put(ChannelGlobal.CHANNEL_CLASS_ID, NamedTextColor.WHITE);
		mDefaultMessageFormats.put(ChannelGlobal.CHANNEL_CLASS_ID, "<gray><click:run_command\":<message_gui_cmd>\"><hover:show_text:\"<channel_color>Global Channel\nClick for GUI\">\\<<channel_color><channel_name><gray>></hover></click> <white><sender> <gray>\u00bb");
		mDefaultMessageColors.put(ChannelLocal.CHANNEL_CLASS_ID, NamedTextColor.YELLOW);
		mDefaultMessageFormats.put(ChannelLocal.CHANNEL_CLASS_ID, "<gray><click:run_command:\"<message_gui_cmd>\"><hover:show_text:\"<channel_color>Local Channel\nClick for GUI\">\\<<channel_color><channel_name><gray>></hover></click> <white><sender> <gray>\u00bb");
		mDefaultMessageColors.put(ChannelParty.CHANNEL_CLASS_ID, NamedTextColor.LIGHT_PURPLE);
		mDefaultMessageFormats.put(ChannelParty.CHANNEL_CLASS_ID, "<gray><click:run_command:\"<message_gui_cmd>\"><hover:show_text:\"<channel_color>Party Channel\nClick for GUI\">\\<<channel_color><channel_name><gray>></hover></click> <white><sender> <gray>\u00bb");
		mDefaultMessageColors.put(ChannelTeam.CHANNEL_CLASS_ID, NamedTextColor.WHITE);
		mDefaultMessageFormats.put(ChannelTeam.CHANNEL_CLASS_ID, "<channel_color><team_displayname> \\<<sender>>");
		mDefaultMessageColors.put(ChannelWhisper.CHANNEL_CLASS_ID, NamedTextColor.GRAY);
		mDefaultMessageFormats.put(ChannelWhisper.CHANNEL_CLASS_ID, "<channel_color><sender> whispers to <receiver> <gray>\u00bb");

		mMessageColors.putAll(mDefaultMessageColors);
		mMessageFormats.putAll(mDefaultMessageFormats);

		try {
			mGlobalChatFilter.addFilter(Bukkit.getConsoleSender(),
				                        "LOG4J_EXPLOIT",
				                        false,
				                        "\\{jndi:.*\\}",
				                        true);
			mGlobalChatFilter.getFilter("LOG4J_EXPLOIT").command("auditlogsevereplayer @S \"@S attempted a Log4J exploit\"");
		} catch (WrapperCommandSyntaxException e) {
			MessagingUtils.sendStackTrace(Bukkit.getConsoleSender(), e);
		}

		try {
			mGlobalChatFilter.addFilter(Bukkit.getConsoleSender(),
				"N_WORD",
				false,
				"n[i1]gg+(a|[e3]r)",
				true);
			ChatFilter.ChatFilterPattern filter = mGlobalChatFilter.getFilter("N_WORD");
			filter.command("auditlogsevereplayer @S \"@S said the N word: @OE\"");
			filter.replacementMessage("<red>cool kid</red>");
		} catch (WrapperCommandSyntaxException e) {
			MessagingUtils.sendStackTrace(Bukkit.getConsoleSender(), e);
		}

		try {
			mGlobalChatFilter.addFilter(Bukkit.getConsoleSender(),
				"URL",
				false,
				"https?://[!#-&(-;=?-\\[\\]-z|~]+",
				false);
			ChatFilter.ChatFilterPattern filter = mGlobalChatFilter.getFilter("URL");
			filter.replacementMessage("<blue><u><click:open_url:\"$0\">$0</click></u></blue>");
		} catch (WrapperCommandSyntaxException e) {
			MessagingUtils.sendStackTrace(Bukkit.getConsoleSender(), e);
		}

		@Nullable ZipFile zip = null;
		try {
			zip = new ZipFile(getFile());
		} catch (IOException ex) {
			getLogger().log(Level.SEVERE, "Could not load help data from plugin.");
		}
		ChatCommand.register(this, zip);
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

		RedisAPI.getInstance().async().hget(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_CHAT_FILTERS_KEY)
			.thenApply(dataStr -> {
			if (dataStr != null) {
				Gson gson = new Gson();
				final JsonObject dataJson = gson.fromJson(dataStr, JsonObject.class);
				Bukkit.getServer().getScheduler().runTask(INSTANCE,
					() -> mGlobalChatFilter = ChatFilter.fromJson(Bukkit.getConsoleSender(), dataJson));
			}
			return dataStr;
		});
	}

	@Override
	public void onDisable() {
		NetworkChatProperties.save(this);
		INSTANCE = null;
		getServer().getScheduler().cancelTasks(this);
	}

	public static NetworkChatPlugin getInstance() {
		return INSTANCE;
	}

	public static int getMessageTtl() {
		return 5;
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
			@Nullable TextColor color = MessagingUtils.colorFromString(value);
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
			NetworkRelayAPI.sendExpiringBroadcastMessage(NETWORK_CHAT_CONFIG_UPDATE,
			                                             wrappedConfigJson,
			                                             getMessageTtl());
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
			NetworkRelayAPI.sendExpiringBroadcastMessage(NETWORK_CHAT_CONFIG_UPDATE,
			                                             wrappedConfigJson,
			                                             getMessageTtl());
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

	public static ChatFilter globalFilter() {
		return mGlobalChatFilter;
	}

	public static void globalFilterFromJson(JsonObject dataJson) {
		mGlobalChatFilter = ChatFilter.fromJson(Bukkit.getConsoleSender(), dataJson);
	}

	public static void saveGlobalFilter() {
		JsonObject dataJson = mGlobalChatFilter.toJson();

		RedisAPI.getInstance().async().hset(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_CHAT_FILTERS_KEY, dataJson.toString());

		JsonObject wrappedConfigJson = new JsonObject();
		wrappedConfigJson.add(REDIS_CHAT_FILTERS_KEY, dataJson);
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(NETWORK_CHAT_CONFIG_UPDATE,
			                                             wrappedConfigJson,
			                                             getMessageTtl());
		} catch (Exception e) {
			INSTANCE.getLogger().severe("Failed to broadcast " + NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void networkRelayMessageEvent(NetworkRelayMessageEvent event) {
		@Nullable JsonObject data;
		switch (event.getChannel()) {
			case NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE -> {
				data = event.getData();
				if (data == null) {
					getLogger().severe("Got " + NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE + " channel with null data");
					return;
				}
				@Nullable JsonObject messageColorsJson = data.getAsJsonObject(REDIS_MESSAGE_COLORS_KEY);
				if (messageColorsJson != null) {
					colorsFromJson(messageColorsJson);
				}
				@Nullable JsonObject messageFormatsJson = data.getAsJsonObject(REDIS_MESSAGE_FORMATS_KEY);
				if (messageFormatsJson != null) {
					formatsFromJson(messageFormatsJson);
				}
				@Nullable JsonObject chatFilterJson = data.getAsJsonObject(REDIS_CHAT_FILTERS_KEY);
				if (chatFilterJson != null) {
					globalFilterFromJson(chatFilterJson);
				}
			}
			default -> {
			}
		}
	}
}
