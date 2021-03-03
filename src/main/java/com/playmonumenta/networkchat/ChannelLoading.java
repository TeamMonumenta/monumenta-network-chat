package com.playmonumenta.networkchat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.NetworkRelayAPI;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.TextArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

// A channel that has not finished loading. This may not be saved.
public class ChannelLoading extends ChannelBase {
	public static final String CHANNEL_CLASS_ID = "loading";

	private UUID mUUID;

	protected ChannelLoading(UUID uuid) {
		mUUID = uuid;
	}

	// NOTE: This channel type should never be saved, as it could overwrite a real channel.
	// toJson() shouldn't even be called for this class.
	public JsonObject toJson() {
		return null;
	}

	// NOTE: If this class ID is ever read, it means the channel hasn't loaded and should be ignored.
	public static String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	public UUID getUniqueId() {
		return mUUID;
	}

	protected void setName(String name) throws WrapperCommandSyntaxException {
		CommandAPI.fail("This channel is still loading, please try again.");
	}

	// NOTE: This should not be called for this class.
	public String getName() {
		return null;
	}

	public void sendMessage(CommandSender sender, String message) throws WrapperCommandSyntaxException {
		CommandAPI.fail("This channel is still loading, please try again.");
	}

	// Messages will be replayed for anyone triggering the channel to load, nothing to do.
	public void distributeMessage(Message message) {
		;
	}

	// The channel is loading - we can't determine who can see this yet!
	protected void showMessage(CommandSender recipient, Message message) {
		;
	}
}
