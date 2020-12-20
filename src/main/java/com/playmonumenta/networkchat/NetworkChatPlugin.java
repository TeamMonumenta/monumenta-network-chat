package com.playmonumenta.networkchat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class NetworkChatPlugin extends JavaPlugin {
	@Override
	public void onLoad() {
		GlobalChatCommand.register(this);
	}

	@Override
	public void onEnable() {
		File configFile = new File(getDataFolder(), "config.yml");

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

		/* Echo config */
		getLogger().info("join-messages-enabled=" + joinMessagesEnabled);

		getServer().getPluginManager().registerEvents(new ChatListener(joinMessagesEnabled), this);
		getServer().getPluginManager().registerEvents(new RedisDataListener(this), this);
	}

	@Override
	public void onDisable() {
		getServer().getScheduler().cancelTasks(this);
	}
}
