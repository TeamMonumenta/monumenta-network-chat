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

// A channel visible only to this shard (and moderators who opt in from elsewhere)
public class ChannelLocal extends ChannelBase {
	public static final String CHANNEL_CLASS_ID = "local";

	private UUID mUUID;
	private String mShardName;
	private String mName;

	private ChannelLocal(UUID uuid, String shardName, String name) {
		mUUID = uuid;
		mShardName = shardName;
		mName = name;
	}

	public ChannelLocal(String name) throws Exception {
		mUUID = UUID.randomUUID();
		mShardName = NetworkRelayAPI.getShardName();
		mName = name;
	}

	protected static ChannelBase fromJsonInternal(JsonObject channelJson) throws Exception {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();
		if (channelClassId == null || !channelClassId.equals(CHANNEL_CLASS_ID)) {
			throw new Exception("Cannot create ChannelLocal from channel ID " + channelClassId);
		}
		String uuidString = channelJson.getAsJsonPrimitive("uuid").getAsString();
		UUID uuid = UUID.fromString(uuidString);
		String shardName = channelJson.getAsJsonPrimitive("shardName").getAsString();
		String name = channelJson.getAsJsonPrimitive("name").getAsString();
		return new ChannelLocal(uuid, shardName, name);
	}

	public static void registerNewChannelCommands(String[] baseCommands, List<Argument> prefixArguments) {
		List<Argument> arguments;

		for (String baseCommand : baseCommands) {
			arguments = new ArrayList<>(prefixArguments);
			// last element of prefixArguments is channel ID
			arguments.add(new MultiLiteralArgument(CHANNEL_CLASS_ID));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String)args[prefixArguments.size()-1];
					ChannelLocal newChannel = null;
					// TODO Perms check

					// Ignore [prefixArguments.size()] ID vs shorthand, they both mean the same thing.
					try {
						newChannel = new ChannelLocal(channelName);
					} catch (Exception e) {
						CommandAPI.fail("Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
					}
					// Throws an exception if the channel already exists, failing the command.
					ChannelManager.registerNewChannel(newChannel);
				})
				.register();
		}
	}

	public JsonObject toJson() {
		JsonObject result = new JsonObject();
		result.addProperty("type", CHANNEL_CLASS_ID);
		result.addProperty("uuid", mUUID.toString());
		result.addProperty("shardName", mShardName);
		result.addProperty("name", mName);
		return result;
	}

	public static String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	public UUID getUniqueId() {
		return mUUID;
	}

	public String getShardName() {
		return mShardName;
	}

	protected void setName(String name) throws WrapperCommandSyntaxException {
		mName = name;
	}

	public String getName() {
		return mName;
	}

	public void sendMessage(CommandSender sender, String message) throws WrapperCommandSyntaxException {
		// TODO Add permission check for local chat.
		Message Message = new Message(this, sender, message);
		// TODO Broadcast for logging purposes, then distribute when the message returns.
		distributeMessage(Message);
	}

	public void distributeMessage(Message message) {
		// TODO Check permission to see the message.
		ChannelBase messageChannel = message.getChannel();
		try {
			if (!(messageChannel instanceof ChannelLocal) ||
				!(((ChannelLocal) messageChannel).mShardName.equals(NetworkRelayAPI.getShardName()))) {
				return;
			}
		} catch (Exception e) {
			// Not connected to RabbitMQ - if this happens, this function doesn't get called anyways.
			return;
		}
		for (Map.Entry<UUID, PlayerState> playerStateEntry : PlayerStateManager.getPlayerStates().entrySet()) {
			UUID playerId = playerStateEntry.getKey();
			PlayerState state = playerStateEntry.getValue();

			if (state.isListening(this)) {
				state.receiveMessage(message);
			}
		}
	}

	protected void showMessage(CommandSender recipient, Message message) {
		// TODO Use configurable formatting, not hard-coded formatting.
		recipient.sendMessage("§7<§f" + mName + " (l)§7> §f" + message.getSender() + " §7»§f " + message.getMessage());
	}
}
