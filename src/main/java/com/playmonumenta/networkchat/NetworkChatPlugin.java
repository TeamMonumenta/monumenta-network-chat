package com.playmonumenta.networkchat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class NetworkChatPlugin extends JavaPlugin {
	// Config is partially handled in other classes, such as ChannelManager
	public static final String NETWORK_CHAT_CONFIG_UPDATE = "com.playmonumenta.networkchat.config_update";
	protected static final String REDIS_CONFIG_PATH = "networkchat:config";

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
}
