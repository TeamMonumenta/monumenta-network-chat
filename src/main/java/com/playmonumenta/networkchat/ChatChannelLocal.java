package com.playmonumenta.networkchat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.TextArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

public class ChatChannelLocal extends ChatChannelBase {
	public static final String CHANNEL_CLASS_ID = "local";

	private UUID mUUID;
	private String mName;

	private ChatChannelLocal(UUID uuid, String name) {
		mUUID = uuid;
		mName = name;
	}

	public ChatChannelLocal(String name) {
		mUUID = UUID.randomUUID();
		mName = name;
	}

	protected static ChatChannelLocal fromJsonInternal(JsonObject channelJson) throws Exception {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();
		if (channelClassId == null || !channelClassId.equals(CHANNEL_CLASS_ID)) {
			throw new Exception("Cannot create ChatChannelLocal from channel ID " + channelClassId);
		}
		String uuidString = channelJson.getAsJsonPrimitive("uuid").getAsString();
		UUID uuid = UUID.fromString(uuidString);
		String name = channelJson.getAsJsonPrimitive("name").getAsString();
		return new ChatChannelLocal(uuid, name);
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
					// TODO Perms check

					// Ignore [prefixArguments.size()] ID vs shorthand, they both mean the same thing.
					ChatChannelLocal newChannel = new ChatChannelLocal((String)args[prefixArguments.size()-1]);
					// Throws an exception if the channel already exists, failing the command.
					ChatManager.registerNewChannel(newChannel);
				})
				.register();
		}
	}

	public JsonObject toJson() {
		JsonObject result = new JsonObject();
		result.addProperty("type", CHANNEL_CLASS_ID);
		result.addProperty("uuid", mUUID.toString());
		result.addProperty("name", mName);
		return result;
	}

	public static String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	public UUID getUniqueId() {
		return mUUID;
	}

	protected void setName(String name) throws WrapperCommandSyntaxException {
		mName = name;
	}

	public String getName() {
		return mName;
	}

	public boolean sendMessage(CommandSender sender, String message) {
		// TODO Add permission check for local chat.
		ChatMessage chatMessage = new ChatMessage(this, sender, message);
		// TODO Broadcast for logging purposes.
		distributeMessage(chatMessage);

		return true;
	}

	public void distributeMessage(ChatMessage message) {
		// TODO Check permission to see the message.
		ChatManager chatManager = ChatManager.getInstance();
		for (Map.Entry<UUID, PlayerChatState> playerStateEntry : ChatManager.getInstance().getPlayerStates().entrySet()) {
			UUID playerId = playerStateEntry.getKey();
			PlayerChatState state = playerStateEntry.getValue();

			if (state.isListening(this)) {
				state.receiveMessage(message);
			}
		}
	}

	protected void showMessage(CommandSender recipient, ChatMessage message) {
		// TODO Use configurable formatting, not hard-coded formatting.
		recipient.sendMessage("§7<§f" + mName + " (l)§7> §f" + message.getSender() + " §7»§f " + message.getMessage());
	}
}
