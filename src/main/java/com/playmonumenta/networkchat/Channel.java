package com.playmonumenta.networkchat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

public abstract class Channel {
	public static Channel fromJson(JsonObject channelJson) throws Exception {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();

		if (channelClassId.equals(ChannelAnnouncement.CHANNEL_CLASS_ID)) {
			return ChannelAnnouncement.fromJsonInternal(channelJson);
		} else if (channelClassId.equals(ChannelGlobal.CHANNEL_CLASS_ID)) {
			return ChannelGlobal.fromJsonInternal(channelJson);
		} else if (channelClassId.equals(ChannelLocal.CHANNEL_CLASS_ID)) {
			return ChannelLocal.fromJsonInternal(channelJson);
		} else if (channelClassId.equals(ChannelParty.CHANNEL_CLASS_ID)) {
			return ChannelParty.fromJsonInternal(channelJson);
		} else if (channelClassId.equals(ChannelWhisper.CHANNEL_CLASS_ID)) {
			return ChannelWhisper.fromJsonInternal(channelJson);
		} else {
			return ChannelFuture.fromJsonInternal(channelJson);
		}
	}

	// OVERRIDE ME - Load a channel from json, allowing messages in that channel to be received
	protected static Channel fromJsonInternal(JsonObject channelJson) throws Exception {
		throw new Exception("Channel has no fromJsonInternal() method!");
	}

	public abstract JsonObject toJson();

	// OVERRIDE ME - Register commands for new channels; continues off an existing argument list of literals.
	// Channel ID is at index = prefixArguments.size() - 1
	public static void registerNewChannelCommands(String[] baseCommands, List<Argument> prefixArguments) {}

	public abstract String getClassId();

	// Return this channel's UUID
	public abstract UUID getUniqueId();

	public void markModified() {}

	// Used to make sure this is the latest version
	public Instant lastModified() {
		return Instant.MIN;
	}

	// Set this channel's name (MUST ONLY be called from ChannelManager).
	// May call CommandAPI.fail() to cancel, ie for direct messages or insufficient permissions.
	protected abstract void setName(String name) throws WrapperCommandSyntaxException;

	// Return this channel's name
	public abstract String getName();

	public ChannelSettings channelSettings() {
		return null;
	}

	public ChannelSettings playerSettings(Player player) {
		return null;
	}

	public ChannelPerms channelPerms() {
		return null;
	}

	public ChannelPerms playerPerms(UUID playerId) {
		return null;
	}

	public void clearPlayerPerms(UUID playerId) {}

	public boolean mayManage(CommandSender sender) {
		return sender.hasPermission("networkchat.moderator");
	}

	public boolean mayChat(CommandSender sender) {
		return false;
	}

	public boolean mayListen(CommandSender sender) {
		return false;
	}

	// Check for access, then send a message to the network.
	// This broadcasts the message without displaying for network messages.
	public abstract void sendMessage(CommandSender sender, String message) throws WrapperCommandSyntaxException;

	// Distributes a received message to the appropriate local player chat states. May be local or remote messages.
	// Note that sending to player chat state allows chat to be paused.
	public abstract void distributeMessage(Message message);

	// Show a message to a player immediately; must be called from Message via PlayerState, not directly.
	protected abstract void showMessage(CommandSender recipient, Message message);
}
