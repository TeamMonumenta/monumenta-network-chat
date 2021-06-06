package com.playmonumenta.networkchat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class NetworkChatPlugin extends JavaPlugin {
	private static NetworkChatPlugin INSTANCE = null;
	public ChannelManager mChannelManager = null;
	public MessageManager mMessageManager = null;
	public PlayerStateManager mPlayerStateManager = null;
	public RemotePlayerManager mRemotePlayerManager = null;

	@Override
	public void onLoad() {
		ChatCommand.register();
	}

	@Override
	public void onEnable() {
		INSTANCE = this;
		File configFile = new File(getDataFolder(), "config.yml");

		/* Check for Placeholder API */
		if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			getLogger().log(Level.SEVERE, "Could not find PlaceholderAPI! This plugin is required.");
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		/* Create the config file & directories if it does not exist */
		if (!configFile.exists()) {
			try {
				// Create parent directories if they do not exist
				configFile.getParentFile().mkdirs();

				// Copy the default config file
				Files.copy(getClass().getResourceAsStream("/default_config.yml"), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ex) {
				getLogger().log(Level.SEVERE, "Failed to create configuration file");
			}
		}

		/* Load the config file & parse it */
		YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

		boolean joinMessagesEnabled = config.getBoolean("join-messages-enabled", true);
		boolean isDefaultChat = config.getBoolean("is-default-chat", true);

		/* Echo config */
		getLogger().info("join-messages-enabled=" + joinMessagesEnabled);
		getLogger().info("is-default-chat=" + isDefaultChat);

		mChannelManager = ChannelManager.getInstance(this);
		mMessageManager = MessageManager.getInstance(this);
		mPlayerStateManager = PlayerStateManager.getInstance(this);
		mPlayerStateManager.isDefaultChat(isDefaultChat);
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
}
