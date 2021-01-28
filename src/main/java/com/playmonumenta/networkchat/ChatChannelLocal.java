package com.playmonumenta.networkchat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.TextArgument;

public class ChatChannelLocal extends ChatChannelBase {
	public static final String CHANNEL_CLASS_ID = "local";
	public static final String CHANNEL_CLASS_SHORTHAND = "l";

	private String mChannelId;

	public ChatChannelLocal(String channelId) {
		mChannelId = channelId;
	}

	public static ChatChannelLocal fromJsonInternal(JsonObject channelJson) throws Exception {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();
		if (!channelClassId.equals(CHANNEL_CLASS_ID)) {
			throw new Exception("Cannot create ChatChannelLocal from channel ID " + channelClassId);
		}
		String channelId = channelJson.getAsJsonPrimitive("channel").getAsString();
		return new ChatChannelLocal(channelId);
	}

	public static void registerNewChannelCommands(String[] baseCommands, List<Argument> prefixArguments) {
		List<Argument> arguments;

		for (String baseCommand : baseCommands) {
			arguments = new ArrayList<>(prefixArguments);
			arguments.add(new MultiLiteralArgument(CHANNEL_CLASS_ID, CHANNEL_CLASS_SHORTHAND));
			arguments.add(new TextArgument("Channel ID"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					// Ignore [prefixArguments.size()] ID vs shorthand, they both mean the same thing.
					ChatManager.registerNewChannel(new ChatChannelLocal((String)args[prefixArguments.size()+1]));
				})
				.register();
		}
	}

	public JsonObject toJson() {
		JsonObject result = new JsonObject();
		result.addProperty("type", CHANNEL_CLASS_ID);
		result.addProperty("channel", mChannelId);
		return result;
	}

	public static String getChannelClassId() {
		return CHANNEL_CLASS_ID;
	}

	public static String getChannelClassShorthand() {
		return CHANNEL_CLASS_SHORTHAND;
	}

	public String getChannelId() {
		return mChannelId;
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
		for (Map.Entry<Player, PlayerChatState> playerStateEntry : ChatManager.getInstance().mPlayerStates.entrySet()) {
			Player player = playerStateEntry.getKey();
			PlayerChatState state = playerStateEntry.getValue();

			state.receiveMessage(message);
		}
	}

	protected void showMessage(CommandSender recipient, ChatMessage message) {
		// TODO Use configurable formatting, not hard-coded formatting.
		recipient.sendMessage("§7<§fL - " + mChannelId + "§7> §f" + message.getSender() + " §7»§f" + message);
	}
}
