package com.playmonumenta.networkchat;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

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
	public enum FlagKey {
		IS_LISTENING("is_listening"),
		MESSAGES_PLAY_SOUND("messages_play_sound");

		String mKey;

		FlagKey(String s) {
			mKey = s;
		}

		public static FlagKey of(String s) {
			try {
				return valueOf(s.toUpperCase());
			} catch (Exception e) {
				return null;
			}
		}

		public String getKey() {
			return mKey;
		}
	}

	public enum FlagValue {
		DEFAULT("default"),
		FALSE("false"),
		TRUE("true");

		String mValue;

		FlagValue(String s) {
			mValue = s;
		}

		public static FlagValue of(String s) {
			try {
				return valueOf(s.toUpperCase());
			} catch (Exception e) {
				return null;
			}
		}

		public String getValue() {
			return mValue;
		}
	}

	// TODO Allow specifying a sound.

	private Map<FlagKey, Boolean> mFlags = new HashMap<>();

	public static ChannelSettings fromJson(JsonObject object) {
		ChannelSettings settings = new ChannelSettings();
		if (object != null) {
			for (Map.Entry<String, JsonElement> settingsEntry : object.entrySet()) {
				String key = settingsEntry.getKey();
				JsonElement valueJson = settingsEntry.getValue();

				FlagKey flagKey = FlagKey.of(key);
				if (flagKey != null && valueJson.isJsonPrimitive()) {
					JsonPrimitive valueJsonPrimitive = valueJson.getAsJsonPrimitive();
					if (valueJsonPrimitive != null && valueJsonPrimitive.isBoolean()) {
						settings.mFlags.put(flagKey, valueJsonPrimitive.getAsBoolean());
					}
				}
			}
		}
		return settings;
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		for (Map.Entry<FlagKey, Boolean> entry : mFlags.entrySet()) {
			String keyStr = entry.getKey().getKey();
			Boolean value = entry.getValue();
			if (value != null) {
				object.addProperty(keyStr, value);
			}
		}
		return object;
	}

	public boolean isDefault() {
		for (Map.Entry<FlagKey, Boolean> entry : mFlags.entrySet()) {
			if (entry.getValue() != null) {
				return false;
			}
		}
		return true;
	}

	public static String[] getFlagKeys() {
		return Stream.of(FlagKey.values()).map(FlagKey::getKey).toArray(String[]::new);
	}

	public static String[] getFlagValues() {
		return Stream.of(FlagValue.values()).map(FlagValue::getValue).toArray(String[]::new);
	}

	public Boolean getFlag(String key) {
		return mFlags.get(FlagKey.of(key));
	}

	public void setFlag(String key, Boolean value) {
		FlagKey flagKey = FlagKey.of(key);
		if (flagKey != null) {
			if (value == null) {
				mFlags.remove(flagKey);
			} else {
				mFlags.put(flagKey, value);
			}
		}
	}

	public Boolean isListening() {
		return getFlag(FlagKey.IS_LISTENING.getKey());
	}

	public void isListening(Boolean value) {
		setFlag(FlagKey.IS_LISTENING.getKey(), value);
	}

	public Boolean messagesPlaySound() {
		return getFlag(FlagKey.MESSAGES_PLAY_SOUND.getKey());
	}

	public void messagesPlaySound(Boolean value) {
		setFlag(FlagKey.MESSAGES_PLAY_SOUND.getKey(), value);
	}

	public int commandFlag(CommandSender sender, String setting) throws WrapperCommandSyntaxException {
		if (FlagKey.of(setting) != null) {
			Boolean value = getFlag(setting);
			if (value == null) {
				sender.sendMessage(Component.empty()
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" is set to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.DEFAULT.getValue(), NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 0;
			} else if (value) {
				sender.sendMessage(Component.empty()
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" is set to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.TRUE.getValue(), NamedTextColor.GREEN, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 1;
			} else {
				sender.sendMessage(Component.empty()
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" is set to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.FALSE.getValue(), NamedTextColor.RED, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return -1;
			}
		}

		CommandAPI.fail("No such setting: " + setting);
		return 0;
	}

	public int commandFlag(CommandSender sender, String setting, String value) throws WrapperCommandSyntaxException {
		if (FlagKey.of(setting) != null) {
			if (FlagValue.FALSE.getValue().equals(value)) {
				setFlag(setting, false);
				sender.sendMessage(Component.empty()
				    .append(Component.text("Set ", NamedTextColor.GRAY))
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.FALSE.getValue(), NamedTextColor.RED, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return -1;
			} else if (FlagValue.TRUE.getValue().equals(value)) {
				setFlag(setting, true);
				sender.sendMessage(Component.empty()
				    .append(Component.text("Set ", NamedTextColor.GRAY))
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.TRUE.getValue(), NamedTextColor.GREEN, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 1;
			} else if (FlagValue.DEFAULT.getValue().equals(value)) {
				setFlag(setting, null);
				sender.sendMessage(Component.empty()
				    .append(Component.text("Set ", NamedTextColor.GRAY))
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.DEFAULT.getValue(), NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 0;
			} else {
				sender.sendMessage(Component.empty()
				    .append(Component.text("Invalid value ", NamedTextColor.RED))
				    .append(Component.text(value, NamedTextColor.RED, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.RED)));
				return 0;
			}
		}

		CommandAPI.fail("No such setting: " + setting);
		return 0;
	}
}
