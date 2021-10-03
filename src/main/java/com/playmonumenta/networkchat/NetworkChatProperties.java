package com.playmonumenta.networkchat;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.utils.FileUtils;

import org.bukkit.command.CommandSender;
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
		JsonObject object = null;
		try {
			object = FileUtils.readJson("NetworkChatProperties.json");
		} catch (Exception e) {
			//TODO-handle exceptions
		}

		mChatCommandCreateEnabled = getPropertyValueBool(plugin, object, "ChatCommandCreate", mChatCommandCreateEnabled);
		mChatCommandModifyEnabled = getPropertyValueBool(plugin, object, "ChatCommandModify", mChatCommandModifyEnabled);
		mChatCommandDeleteEnabled = getPropertyValueBool(plugin, object, "ChatCommandDelate", mChatCommandDeleteEnabled);

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

	private boolean getPropertyValueBool(Plugin plugin, JsonObject object, String properyName, boolean defaultVal) {
		boolean value = defaultVal;
		if (object != null) {
			JsonElement element = object.get(properyName);
			if (element != null) {
				value = element.getAsBoolean();
			}
		}

		return value;
	}
}
