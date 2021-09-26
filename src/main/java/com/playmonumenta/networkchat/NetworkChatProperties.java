package com.playmonumenta.networkchat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public class NetworkChatProperties {

	private static NetworkChatProperties INSTANCE = null;

	private boolean mChatCommandCreateEnabled = true;
	private boolean mChatCommandModifyEnabled = true;
	private boolean mChatCommandDeleteEnabled = true;

	public NetworkChatProperties() {
		INSTANCE = this;
	}

	private static void ensureInstance() {
		if (INSTANCE == null) {
			new NetworkChatProperties();
		}
	}

	public static boolean getChatCommandCreateEnabled() {
		ensureInstance();
		return INSTANCE.mChatCommandCreateEnabled;
	}

	public static boolean getChatCommandModifyEnabled() {
		ensureInstance();
		return INSTANCE.mChatCommandModifyEnabled;
	}

	public static boolean getChatCommandDeleteEnabled() {
		ensureInstance();
		return INSTANCE.mChatCommandDeleteEnabled;
	}

	public static void load(Plugin plugin, CommandSender sender) {
		ensureInstance();
		INSTANCE.loadInternal(plugin, sender);
	}

	private void loadInternal(Plugin plugin, CommandSender sender) {
		File configFile = new File(plugin.getDataFolder(), "config.yml");
		FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

		if (config.isBoolean("ChatCommandCreate")) {
			mChatCommandCreateEnabled = config.getBoolean("ChatCommandCreate", mChatCommandCreateEnabled);
		}

		if (config.isBoolean("ChatCommandModify")) {
			mChatCommandModifyEnabled = config.getBoolean("ChatCommandModify", mChatCommandModifyEnabled);
		}

		if (config.isBoolean("ChatCommandDelate")) {
			mChatCommandDeleteEnabled = config.getBoolean("ChatCommandDelate", mChatCommandDeleteEnabled);
		}

		plugin.getLogger().info("Properties:");
		if (sender != null) {
			sender.sendMessage("Properties:");
		}
		for (String str : toDisplay()) {
			plugin.getLogger().info("  " + str);
			if (sender != null) {
				sender.sendMessage("  " + str);
			}
		}
	}

	private List<String> toDisplay() {
		List<String> out = new ArrayList<>();

		out.add("mChatCommandCreateEnabled = " + mChatCommandCreateEnabled);
		out.add("mChatCommandModifyEnabled = " + mChatCommandModifyEnabled);
		out.add("mChatCommandDeleteEnabled = " + mChatCommandDeleteEnabled);

		return out;
	}

}
