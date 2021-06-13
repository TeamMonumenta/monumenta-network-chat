package com.playmonumenta.networkchat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.command.CommandSender;

// Settings for which channel to use for a given type, and for chatting without a command
public class DefaultChannels {
	public static final String DEFAULT_CHANNEL = "default";
	public static final Set<String> CHANNEL_TYPES = Set.of(DEFAULT_CHANNEL,
		ChannelAnnouncement.CHANNEL_CLASS_ID,
		ChannelLocal.CHANNEL_CLASS_ID,
		ChannelGlobal.CHANNEL_CLASS_ID,
		ChannelParty.CHANNEL_CLASS_ID);

	private Map<String, UUID> mDefaultsByType = new HashMap<>();

	public static DefaultChannels fromJson(JsonObject object) {
		DefaultChannels defaults = new DefaultChannels();
		if (object != null) {
			for (Map.Entry<String, JsonElement> defaultsEntry : object.entrySet()) {
				String key = defaultsEntry.getKey();
				JsonElement valueJson = defaultsEntry.getValue();

				try {
					defaults.mDefaultsByType.put(key, UUID.fromString(valueJson.getAsString()));
				} catch (Exception e) {
					;
				}
			}
		}
		return defaults;
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		for (Map.Entry<String, UUID> entry : mDefaultsByType.entrySet()) {
			String key = entry.getKey();
			UUID value = entry.getValue();
			if (value != null) {
				object.addProperty(key, value.toString());
			}
		}
		return object;
	}

	public boolean isDefault() {
		for (Map.Entry<String, UUID> entry : mDefaultsByType.entrySet()) {
			if (entry.getValue() != null) {
				return false;
			}
		}
		return true;
	}

	public Channel getDefaultChannel(String key) {
		UUID channelId = mDefaultsByType.get(key);
		if (channelId == null) {
			return null;
		}
		return ChannelManager.getChannel(channelId);
	}

	public UUID getDefaultId(String key) {
		return mDefaultsByType.get(key);
	}

	public void setDefaultChannel(Channel channel) {
		mDefaultsByType.put(DEFAULT_CHANNEL, channel.getUniqueId());
	}

	public void setDefaultChannelForType(Channel channel) {
		mDefaultsByType.put(channel.getClassId(), channel.getUniqueId());
	}

	public void setDefaultId(String key, UUID value) {
		if (CHANNEL_TYPES.contains(key)) {
			if (value == null) {
				mDefaultsByType.remove(key);
			} else {
				mDefaultsByType.put(key, value);
			}
		}
	}

	public int command(CommandSender sender, String channelType) throws WrapperCommandSyntaxException {
		return command(sender, channelType, false);
	}

	public int command(CommandSender sender, String channelType, boolean isGlobalDefault) throws WrapperCommandSyntaxException {
		if (CHANNEL_TYPES.contains(channelType)) {
			UUID channelId = getDefaultId(channelType);
			if (channelId == null) {
				if (isGlobalDefault) {
					sender.sendMessage(Component.empty()
						.append(Component.text(channelType, NamedTextColor.AQUA, TextDecoration.BOLD))
						.append(Component.text(" has no default set!", NamedTextColor.RED)));
				} else {
					sender.sendMessage(Component.empty()
						.append(Component.text(channelType, NamedTextColor.AQUA, TextDecoration.BOLD))
						.append(Component.text(" is set to the server default.", NamedTextColor.RED)));
				}
				return 0;
			} else {
				Channel channel = ChannelManager.getChannel(channelId);
				if (channel != null) {
					String channelName = channel.getName();
					sender.sendMessage(Component.empty()
						.append(Component.text(channelType, NamedTextColor.AQUA, TextDecoration.BOLD))
						.append(Component.text(" is set to ", NamedTextColor.GRAY))
						.append(Component.text(channelName, NamedTextColor.AQUA, TextDecoration.BOLD))
						.append(Component.text(".", NamedTextColor.GRAY)));
					return 1;
				} else {
					sender.sendMessage(Component.empty()
						.append(Component.text(channelType, NamedTextColor.AQUA, TextDecoration.BOLD))
						.append(Component.text(" is set to a channel that is not loaded.", NamedTextColor.RED)));
					return 0;
				}
			}
		}

		CommandAPI.fail("No such channel default: " + channelType);
		return 0;
	}

	public int command(CommandSender sender, String channelType, String channelName) throws WrapperCommandSyntaxException {
		if (!CHANNEL_TYPES.contains(channelType)) {
			CommandAPI.fail("No such channel default: " + channelType);
		}

		Channel channel = ChannelManager.getChannel(channelName);
		if (channel == null) {
			CommandAPI.fail("No such channel: " + channelName);
		}

		setDefaultId(channelType, channel.getUniqueId());
		sender.sendMessage(Component.empty()
			.append(Component.text("Set ", NamedTextColor.GRAY))
			.append(Component.text(channelType, NamedTextColor.AQUA, TextDecoration.BOLD))
			.append(Component.text(" to ", NamedTextColor.GRAY))
			.append(Component.text(channelName, NamedTextColor.AQUA, TextDecoration.BOLD))
			.append(Component.text(".", NamedTextColor.GRAY)));
		return 1;
	}
}
