package com.playmonumenta.networkchat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.TextArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

import com.playmonumenta.networkchat.utils.CommandUtils;

public class ChatCommand {
	public static final String[] COMMANDS = new String[]{"chat", "ch"};

	public static void register() {
		List<Argument> arguments = new ArrayList<>();

		arguments.add(new MultiLiteralArgument("new"));
		ChatChannelLocal.registerNewChannelCommands(COMMANDS, arguments);

		for (String baseCommand : COMMANDS) {
			for (String channelType : ChatManager.getChannelClassIds()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("say"));
				arguments.add(new MultiLiteralArgument(channelType));
				arguments.add(new TextArgument("Channel ID").overrideSuggestions((sender) -> {
					return ChatManager.getChannelIds(channelType).toArray(new String[0]);
				}));
				arguments.add(new GreedyStringArgument("Message"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						return sendMessage(sender, (String)args[1], (String)args[2], (String)args[3]);
					})
					.register();

				arguments.clear();
				arguments.add(new MultiLiteralArgument("join"));
				arguments.add(new MultiLiteralArgument(channelType));
				arguments.add(new TextArgument("Channel ID").overrideSuggestions((sender) -> {
					return ChatManager.getChannelIds(channelType).toArray(new String[0]);
				}));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						return joinChannel(sender, (String)args[1], (String)args[2]);
					})
					.register();

				arguments.clear();
				arguments.add(new MultiLiteralArgument("leave"));
				arguments.add(new MultiLiteralArgument(channelType));
				arguments.add(new TextArgument("Channel ID").overrideSuggestions((sender) -> {
					return ChatManager.getChannelIds(channelType).toArray(new String[0]);
				}));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executes((sender, args) -> {
						return leaveChannel(sender, (String)args[1], (String)args[2]);
					})
					.register();
			}
		}
	}

	private static int joinChannel(CommandSender sender, String channelType, String channelId) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
			return 0;
		}

		Player target = (Player) callee;
		PlayerChatState playerState = ChatManager.getPlayerState(target);
		if (playerState == null) {
			CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
			return 0;
		}

		ChatChannelBase channel = ChatManager.getChannel(channelType, channelId);
		if (channel == null) {
			CommandAPI.fail("No such channel " + channelId + " of type " + channelType + ".");
			return 0;
		}

		playerState.joinChannel(channel);
		return 1;
	}

	private static int leaveChannel(CommandSender sender, String channelType, String channelId) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
			return 0;
		}

		Player target = (Player) callee;
		PlayerChatState playerState = ChatManager.getPlayerState(target);
		if (playerState == null) {
			CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
			return 0;
		}

		ChatChannelBase channel = ChatManager.getChannel(channelType, channelId);
		if (channel == null) {
			CommandAPI.fail("No such channel " + channelId + " of type " + channelType + ".");
			return 0;
		}

		playerState.leaveChannel(channel);
		return 1;
	}

	private static int sendMessage(CommandSender sender, String channelType, String channelId, String message) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (sender != callee && callee instanceof Player) {
			CommandAPI.fail("Hey! It's not nice to put words in people's mouths! Where are your manners?");
			return 0;
		}

		ChatChannelBase channel = ChatManager.getChannel(channelType, channelId);
		if (channel == null) {
			CommandAPI.fail("No such channel " + channelId + " of type " + channelType + ".");
			return 0;
		}

		boolean messageSent = channel.sendMessage(sender, message);
		return messageSent ? 1 : 0;
	}
}
