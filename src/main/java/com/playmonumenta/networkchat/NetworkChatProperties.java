package com.playmonumenta.networkchat;

import java.io.File;
import java.io.IOException;
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
	private boolean mSudoEnabled = false;

	public NetworkChatProperties() {
		INSTANCE = this;
	}

	private static void ensureInstance() {
		if (INSTANCE == null) {
			new NetworkChatProperties();
		}
	}

	public static boolean getSudoEnabled() {
		ensureInstance();
		return INSTANCE.mSudoEnabled;
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

		if (config.isBoolean("SudoEnabled")) {
			mSudoEnabled = config.getBoolean("SudoEnabled", mChatCommandDeleteEnabled);
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
		out.add("mSudoEnabled = " + mSudoEnabled);

		return out;
	}


	public static void save(Plugin plugin) {
		ensureInstance();
		INSTANCE.saveConfig(plugin);
	}

	public void saveConfig(Plugin plugin) {
		File configFile = new File(plugin.getDataFolder(), "config.yml");

		if (!configFile.exists()) {
			try {
				configFile.createNewFile();
			} catch (IOException e) {
				plugin.getLogger().warning("Catch exeption during create new file for config.yml. Reason: " + e.getMessage());
			}
		}

		FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

		config.set("ChatCommandCreate", mChatCommandCreateEnabled);
		config.set("ChatCommandModify", mChatCommandModifyEnabled);
		config.set("ChatCommandDelate", mChatCommandDeleteEnabled);
		config.set("SudoEnabled", mChatCommandDeleteEnabled);

		try {
			config.save(configFile);
		} catch (IOException e) {
			plugin.getLogger().warning("Catch exeption while save config.yml. Reason: " + e.getMessage());
		}
	}
}
