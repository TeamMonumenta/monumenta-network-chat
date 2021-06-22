package com.playmonumenta.networkchat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public class NetworkChatPlugin extends JavaPlugin {
	// Config is partially handled in other classes, such as ChannelManager
	public static final String NETWORK_CHAT_CONFIG_UPDATE = "com.playmonumenta.networkchat.config_update";
	protected static final String REDIS_CONFIG_PATH = "networkchat:config";
	private static final String REDIS_MESSAGE_FORMATS_KEY = "message_formats";

	private static NetworkChatPlugin INSTANCE = null;
	public static Map<String, TextColor> mMessageColors = new ConcurrentSkipListMap<>();
	public static Map<String, String> mMessageFormats = new ConcurrentSkipListMap<>();
	public static ChannelManager mChannelManager = null;
	public static MessageManager mMessageManager = null;
	public static PlayerStateManager mPlayerStateManager = null;
	public static RemotePlayerManager mRemotePlayerManager = null;

	@Override
	public void onLoad() {
		// TODO Move these formatting options to a defaults structure, then copy to the final mappings
		mMessageFormats.put("player", "<insert:%player_name%>"
			+ "<click:suggest_command:/tell %player_name% >"
			+ "<hover:show_entity:'minecraft:player':%player_uuid%:%player_name%>"
			+ "<team_color><team_prefix>%player_name%<team_suffix>"
			+ "</hover></click></insert>");

		mMessageColors.put(ChannelAnnouncement.CHANNEL_CLASS_ID, NamedTextColor.RED);
		mMessageFormats.put(ChannelAnnouncement.CHANNEL_CLASS_ID, "<gray><hover:show_text:\"<channel_color>Announcement Channel\">\\<<channel_color><channel_name><gray>></hover>");
		mMessageColors.put(ChannelGlobal.CHANNEL_CLASS_ID, NamedTextColor.WHITE);
		mMessageFormats.put(ChannelGlobal.CHANNEL_CLASS_ID, "<gray><hover:show_text:\"<channel_color>Global Channel\">\\<<channel_color><channel_name><gray>></hover> <white><sender> <gray>»");
		mMessageColors.put(ChannelLocal.CHANNEL_CLASS_ID, NamedTextColor.YELLOW);
		mMessageFormats.put(ChannelLocal.CHANNEL_CLASS_ID, "<gray><hover:show_text:\"<channel_color>Local Channel\">\\<<channel_color><channel_name><gray>></hover> <white><sender> <gray>»");
		mMessageColors.put(ChannelParty.CHANNEL_CLASS_ID, NamedTextColor.LIGHT_PURPLE);
		mMessageFormats.put(ChannelParty.CHANNEL_CLASS_ID, "<gray><hover:show_text:\"<channel_color>Party Channel\">\\<<channel_color><channel_name><gray>></hover> <white><sender> <gray>»");
		mMessageColors.put(ChannelWhisper.CHANNEL_CLASS_ID, NamedTextColor.GRAY);
		mMessageFormats.put(ChannelWhisper.CHANNEL_CLASS_ID, "<channel_color><sender> whispers to <receiver> <gray>»");

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
	}

	@Override
	public void onDisable() {
		INSTANCE = null;
		getServer().getScheduler().cancelTasks(this);
	}

	public static NetworkChatPlugin getInstance() {
		return INSTANCE;
	}

	public static TextColor messageColor(String id) {
		return mMessageColors.get(id);
	}

	public static void messageColor(String id, TextColor value) {
		// TODO Save to Redis and broadcast via RabbitMQ
		mMessageColors.put(id, value);
	}

	public static String messageFormat(String id) {
		return mMessageFormats.get(id);
	}

	public static void messageFormat(String id, String value) {
		// TODO Save to Redis and broadcast via RabbitMQ
		mMessageFormats.put(id, value);
	}
}
