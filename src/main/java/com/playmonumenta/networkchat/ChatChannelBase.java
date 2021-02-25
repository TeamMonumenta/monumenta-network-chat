package com.playmonumenta.networkchat;

import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

public abstract class ChatChannelBase {
	public static ChatChannelBase fromJson(JsonObject channelJson) throws Exception {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();

		if (channelClassId.equals(ChatChannelLocal.CHANNEL_CLASS_ID)) {
			return ChatChannelLocal.fromJsonInternal(channelJson);
		} else {
			throw new Exception("No such chat channel class ID " + channelClassId);
		}
	}

	// DEFINE ME - Load a channel from json, allowing messages in that channel to be received
	//protected static ChatChannelClassHere fromJsonInternal(JsonObject channelJson) throws Exception;

	public abstract JsonObject toJson();

	// OVERRIDE ME - Register commands for new channels; continues off an existing argument list of literals.
	// Channel ID is at index = prefixArguments.size() - 1
	public static void registerNewChannelCommands(String[] baseCommands, List<Argument> prefixArguments) {
		;
	}

	// OVERRIDE ME - Return an identifier for this channel class.
	public static String getClassId() {
		return null;
	}

	// Return this channel's UUID
	public abstract UUID getUniqueId();

	// Set this channel's name (MUST ONLY be called from ChatManager).
	// May call CommandAPI.fail() to cancel, ie for direct messages or insufficient permissions.
	protected abstract void setName(String name) throws WrapperCommandSyntaxException;

	// Return this channel's name
	public abstract String getName();

	// Check for access, then send a message to the network.
	// This broadcasts the message without displaying for network messages.
	public abstract boolean sendMessage(CommandSender sender, String message);

	// Distributes a message to the appropriate player chat states. May be local or remote messages.
	// Note that the message may not be displayed right away if the player has paused their chat.
	public abstract void distributeMessage(ChatMessage message);

	// Show a message to a player; must be called from ChatMessage via PlayerChatState, not directly.
	protected abstract void showMessage(CommandSender recipient, ChatMessage message);
}
