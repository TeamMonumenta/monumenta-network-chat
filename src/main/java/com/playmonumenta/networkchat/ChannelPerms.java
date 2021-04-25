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

// Permissions to listen or talk in a channel
public class ChannelPerms {
	public final static String MAY_CHAT = "may_chat";
	public final static String MAY_LISTEN = "may_listen";

	// For value suggestions
	public final static String FLAG_STR_DEFAULT = "default";
	public final static String FLAG_STR_FALSE = "false";
	public final static String FLAG_STR_TRUE = "true";

	public final static Set<String> mFlagKeys = Collections.unmodifiableSet(
		Set.of(MAY_CHAT, MAY_LISTEN));
	public final static Set<String> mFlagValues = Collections.unmodifiableSet(
		Set.of(FLAG_STR_DEFAULT, FLAG_STR_FALSE, FLAG_STR_TRUE));
	private Map<String, Boolean> mFlags = new HashMap<>();

	private Boolean mMayChat = null;
	private Boolean mMayListen = null;

	public static ChannelPerms fromJson(JsonObject object) {
		ChannelPerms perms = new ChannelPerms();
		if (object != null) {
			for (Map.Entry<String, JsonElement> permsEntry : object.entrySet()) {
				String key = permsEntry.getKey();
				JsonElement valueJson = permsEntry.getValue();

				if (mFlagKeys.contains(key) && valueJson.isJsonPrimitive()) {
					JsonPrimitive valueJsonPrimitive = valueJson.getAsJsonPrimitive();
					if (valueJsonPrimitive != null && valueJsonPrimitive.isBoolean()) {
						perms.mFlags.put(key, valueJsonPrimitive.getAsBoolean());
					}
				}
			}
		}
		return perms;
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

	public Boolean mayChat() {
		return getFlag(MAY_CHAT);
	}

	public void mayChat(Boolean value) {
		setFlag(MAY_CHAT, value);
	}

	public Boolean mayListen() {
		return getFlag(MAY_LISTEN);
	}

	public void mayListen(Boolean value) {
		setFlag(MAY_LISTEN, value);
	}

	public int commandFlag(CommandSender sender, String permission) throws WrapperCommandSyntaxException {
		if (mFlagKeys.contains(permission)) {
			Boolean value = getFlag(permission);
			if (value == null) {
				sender.sendMessage(Component.empty()
				    .append(Component.text(permission, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" is set to ", NamedTextColor.GRAY))
				    .append(Component.text(FLAG_STR_DEFAULT, NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 0;
			} else if (value) {
				sender.sendMessage(Component.empty()
				    .append(Component.text(permission, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" is set to ", NamedTextColor.GRAY))
				    .append(Component.text(FLAG_STR_TRUE, NamedTextColor.GREEN, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 1;
			} else {
				sender.sendMessage(Component.empty()
				    .append(Component.text(permission, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" is set to ", NamedTextColor.GRAY))
				    .append(Component.text(FLAG_STR_FALSE, NamedTextColor.RED, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return -1;
			}
		}

		CommandAPI.fail("No such permission: " + permission);
		return 0;
	}

	public int commandFlag(CommandSender sender, String permission, String value) throws WrapperCommandSyntaxException {
		if (mFlagKeys.contains(permission)) {
			if (FLAG_STR_FALSE.equals(value)) {
				setFlag(permission, false);
				sender.sendMessage(Component.empty()
				    .append(Component.text("Set ", NamedTextColor.GRAY))
				    .append(Component.text(permission, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" to ", NamedTextColor.GRAY))
				    .append(Component.text(FLAG_STR_FALSE, NamedTextColor.RED, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return -1;
			} else if (FLAG_STR_TRUE.equals(value)) {
				setFlag(permission, true);
				sender.sendMessage(Component.empty()
				    .append(Component.text("Set ", NamedTextColor.GRAY))
				    .append(Component.text(permission, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" to ", NamedTextColor.GRAY))
				    .append(Component.text(FLAG_STR_TRUE, NamedTextColor.GREEN, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 1;
			} else {
				setFlag(permission, null);
				sender.sendMessage(Component.empty()
				    .append(Component.text("Set ", NamedTextColor.GRAY))
				    .append(Component.text(permission, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" to ", NamedTextColor.GRAY))
				    .append(Component.text(FLAG_STR_DEFAULT, NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 0;
			}
		}

		CommandAPI.fail("No such permission: " + permission);
		return 0;
	}
}
