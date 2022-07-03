package com.playmonumenta.networkchat;

import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public abstract class Channel {
	public static Channel fromJson(JsonObject channelJson) throws Exception {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();

		return switch (channelClassId) {
			case ChannelAnnouncement.CHANNEL_CLASS_ID -> ChannelAnnouncement.fromJsonInternal(channelJson);
			case ChannelGlobal.CHANNEL_CLASS_ID -> ChannelGlobal.fromJsonInternal(channelJson);
			case ChannelLocal.CHANNEL_CLASS_ID -> ChannelLocal.fromJsonInternal(channelJson);
			case ChannelParty.CHANNEL_CLASS_ID -> ChannelParty.fromJsonInternal(channelJson);
			case ChannelTeam.CHANNEL_CLASS_ID -> ChannelTeam.fromJsonInternal(channelJson);
			case ChannelWhisper.CHANNEL_CLASS_ID -> ChannelWhisper.fromJsonInternal(channelJson);
			case ChannelWorld.CHANNEL_CLASS_ID -> ChannelWorld.fromJsonInternal(channelJson);
			default -> ChannelFuture.fromJsonInternal(channelJson);
		};
	}

	// DEFINE ME - Load a channel from json, allowing messages in that channel to be received
	//protected static Channel fromJsonInternal(JsonObject channelJson) throws Exception;

	public abstract JsonObject toJson();

	// DEFINE ME - Register commands for new channels; continues off an existing argument list of literals.
	// Channel ID is at index = prefixArguments.size() - 1
	//public static void registerNewChannelCommands(String[] baseCommands, List<Argument> prefixArguments);

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

	public abstract @Nullable TextColor color();

	public abstract void color(CommandSender sender, @Nullable TextColor color) throws WrapperCommandSyntaxException;

	public ChannelSettings channelSettings() {
		return null;
	}

	public ChannelAccess channelAccess() {
		return null;
	}

	public ChannelAccess playerAccess(UUID playerId) {
		return null;
	}

	public void resetPlayerAccess(UUID playerId) {}

	public boolean shouldAutoJoin(PlayerState state) {
		Player player = state.getPlayer();
		return player != null && mayListen(player);
	}

	public boolean mayManage(CommandSender sender) {
		return CommandUtils.hasPermission(sender, "networkchat.moderator");
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

	// Get how the message appears to a given recipient.
	protected abstract Component shownMessage(CommandSender recipient, Message message);

	// Show a message to a player immediately; must be called from Message via PlayerState, not directly.
	protected abstract void showMessage(CommandSender recipient, Message message);
}
