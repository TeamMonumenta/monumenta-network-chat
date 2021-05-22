package com.playmonumenta.networkchat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.command.CommandSender;

// Settings for a given channel, same structure for channel default and player preference
public class ChannelSettings {
	public static final String IS_LISTENING = "is_listening";
	// TODO Allow specifying a sound.
	public static final String MESSAGES_PLAY_SOUND = "messages_play_sound";

	// For value suggestions
	public static final String FLAG_STR_DEFAULT = "default";
	public static final String FLAG_STR_FALSE = "false";
	public static final String FLAG_STR_TRUE = "true";

	public static final Set<String> mFlagKeys = Set.of(IS_LISTENING, MESSAGES_PLAY_SOUND);
	public static final Set<String> mFlagValues = Set.of(FLAG_STR_DEFAULT, FLAG_STR_FALSE, FLAG_STR_TRUE);
	private Map<String, Boolean> mFlags = new HashMap<>();

	public static ChannelSettings fromJson(JsonObject object) {
		ChannelSettings settings = new ChannelSettings();
		if (object != null) {
			for (Map.Entry<String, JsonElement> settingsEntry : object.entrySet()) {
				String key = settingsEntry.getKey();
				JsonElement valueJson = settingsEntry.getValue();

				if (mFlagKeys.contains(key) && valueJson.isJsonPrimitive()) {
					JsonPrimitive valueJsonPrimitive = valueJson.getAsJsonPrimitive();
					if (valueJsonPrimitive != null && valueJsonPrimitive.isBoolean()) {
						settings.mFlags.put(key, valueJsonPrimitive.getAsBoolean());
					}
				}
			}
		}
		return settings;
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		for (String key : mFlagKeys) {
			Boolean value = getFlag(key);
			if (value != null) {
				object.addProperty(key, value);
			}
		}
		return object;
	}

	public boolean isDefault() {
		for (Map.Entry<String, Boolean> entry : mFlags.entrySet()) {
			if (entry.getValue() != null) {
				return false;
			}
		}
		return true;
	}

	public Boolean getFlag(String key) {
		return mFlags.get(key);
	}

	public void setFlag(String key, Boolean value) {
		if (mFlagKeys.contains(key)) {
			if (value == null) {
				mFlags.remove(key);
			} else {
				mFlags.put(key, value);
			}
		}
	}

	public Boolean isListening() {
		return getFlag(IS_LISTENING);
	}

	public void isListening(Boolean value) {
		setFlag(IS_LISTENING, value);
	}

	public Boolean messagesPlaySound() {
		return getFlag(MESSAGES_PLAY_SOUND);
	}

	public void messagesPlaySound(Boolean value) {
		setFlag(MESSAGES_PLAY_SOUND, value);
	}

	public int commandFlag(CommandSender sender, String setting) throws WrapperCommandSyntaxException {
		if (mFlagKeys.contains(setting)) {
			Boolean value = getFlag(setting);
			if (value == null) {
				sender.sendMessage(Component.empty()
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" is set to ", NamedTextColor.GRAY))
				    .append(Component.text(FLAG_STR_DEFAULT, NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 0;
			} else if (value) {
				sender.sendMessage(Component.empty()
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" is set to ", NamedTextColor.GRAY))
				    .append(Component.text(FLAG_STR_TRUE, NamedTextColor.GREEN, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 1;
			} else {
				sender.sendMessage(Component.empty()
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" is set to ", NamedTextColor.GRAY))
				    .append(Component.text(FLAG_STR_FALSE, NamedTextColor.RED, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return -1;
			}
		}

		CommandAPI.fail("No such setting: " + setting);
		return 0;
	}

	public int commandFlag(CommandSender sender, String setting, String value) throws WrapperCommandSyntaxException {
		if (mFlagKeys.contains(setting)) {
			if (FLAG_STR_FALSE.equals(value)) {
				setFlag(setting, false);
				sender.sendMessage(Component.empty()
				    .append(Component.text("Set ", NamedTextColor.GRAY))
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" to ", NamedTextColor.GRAY))
				    .append(Component.text(FLAG_STR_FALSE, NamedTextColor.RED, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return -1;
			} else if (FLAG_STR_TRUE.equals(value)) {
				setFlag(setting, true);
				sender.sendMessage(Component.empty()
				    .append(Component.text("Set ", NamedTextColor.GRAY))
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" to ", NamedTextColor.GRAY))
				    .append(Component.text(FLAG_STR_TRUE, NamedTextColor.GREEN, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 1;
			} else {
				setFlag(setting, null);
				sender.sendMessage(Component.empty()
				    .append(Component.text("Set ", NamedTextColor.GRAY))
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" to ", NamedTextColor.GRAY))
				    .append(Component.text(FLAG_STR_DEFAULT, NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 0;
			}
		}

		CommandAPI.fail("No such setting: " + setting);
		return 0;
	}
}
