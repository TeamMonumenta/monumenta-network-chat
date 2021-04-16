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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import com.playmonumenta.networkchat.utils.CommandUtils;

public class ChatCommand {
	public static final String[] COMMANDS = new String[]{"chat", "ch", "chattest"};

	public static void register() {
		List<Argument> arguments = new ArrayList<>();

		arguments.add(new MultiLiteralArgument("new"));
		arguments.add(new TextArgument("Channel ID"));
		ChannelLocal.registerNewChannelCommands(COMMANDS, new ArrayList<Argument>(arguments));

		for (String baseCommand : COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("rename"));
			arguments.add(new TextArgument("Old Channel ID").overrideSuggestions((sender) -> {
				// TODO Only suggest channels players have access to (same with other suggestions)
				return ChannelManager.getChannelNames().toArray(new String[0]);
			}));
			arguments.add(new TextArgument("New Channel ID"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return renameChannel(sender, (String)args[1], (String)args[2]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("forceload"));
			arguments.add(new TextArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getChannelNames().toArray(new String[0]);
			}));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return ChannelManager.forceLoadChannel(sender, (String)args[1]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("delete"));
			arguments.add(new TextArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getChannelNames().toArray(new String[0]);
			}));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return deleteChannel(sender, (String)args[1]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("resetall"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return resetAll(sender);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("say"));
			arguments.add(new TextArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getChannelNames().toArray(new String[0]);
			}));
			arguments.add(new GreedyStringArgument("Message"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					sendMessage(sender, (String)args[1], (String)args[2]);
					return 1;
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("join"));
			arguments.add(new TextArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getChannelNames().toArray(new String[0]);
			}));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return joinChannel(sender, (String)args[1]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("leave"));
			arguments.add(new TextArgument("Channel ID").overrideSuggestions((sender) -> {
				return ChannelManager.getChannelNames().toArray(new String[0]);
			}));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return leaveChannel(sender, (String)args[1]);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("pause"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return pause(sender);
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("unpause"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					return unpause(sender);
				})
				.register();
		}
	}

	private static int renameChannel(CommandSender sender, String oldChannelId, String newChannelId) throws WrapperCommandSyntaxException {
		// TODO Perms check

		// May call CommandAPI.fail()
		ChannelManager.renameChannel(oldChannelId, newChannelId);
		sender.sendMessage(Component.text("Channel " + oldChannelId + " renamed to " + newChannelId + ".", NamedTextColor.GRAY));
		return 1;
	}

	private static int deleteChannel(CommandSender sender, String channelId) throws WrapperCommandSyntaxException {
		// TODO Perms check

		// May call CommandAPI.fail()
		ChannelManager.deleteChannel(channelId);
		sender.sendMessage(Component.text("Channel " + channelId + " deleted.", NamedTextColor.GRAY));
		return 1;
	}

	private static int resetAll(CommandSender sender) throws WrapperCommandSyntaxException {
		// TODO REALLY IMPORTANT PERMS CHECK

		ChannelManager.resetAll();
		sender.sendMessage(Component.text("You did the bad thing! All things reset.", NamedTextColor.RED));
		return 1;
	}

	private static int joinChannel(CommandSender sender, String channelId) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
		}

		Player target = (Player) callee;
		PlayerState playerState = PlayerStateManager.getPlayerState(target);
		if (playerState == null) {
			CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
		}

		ChannelBase channel = ChannelManager.getChannel(channelId);
		if (channel == null) {
			CommandAPI.fail("No such channel " + channelId + ".");
		}

		playerState.joinChannel(channel);
		target.sendMessage(Component.text("Joined channel " + channelId + ".", NamedTextColor.GRAY));
		return 1;
	}

	private static int leaveChannel(CommandSender sender, String channelId) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
		}

		Player target = (Player) callee;
		PlayerState playerState = PlayerStateManager.getPlayerState(target);
		if (playerState == null) {
			CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
		}

		ChannelBase channel = ChannelManager.getChannel(channelId);
		if (channel == null) {
			CommandAPI.fail("No such channel " + channelId + ".");
		}

		playerState.leaveChannel(channel);
		target.sendMessage(Component.text("Left channel " + channelId + ".", NamedTextColor.GRAY));
		return 1;
	}

	private static int sendMessage(CommandSender sender, String channelId, String message) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (callee instanceof Player && sender != callee) {
			CommandAPI.fail("Hey! It's not nice to put words in people's mouths! Where are your manners?");
		}

		ChannelBase channel = ChannelManager.getChannel(channelId);
		if (channel == null) {
			CommandAPI.fail("No such channel " + channelId + ".");
		}

		channel.sendMessage(callee, message);
		return 1;
	}

	private static int pause(CommandSender sender) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
		}

		Player target = (Player) callee;
		PlayerState playerState = PlayerStateManager.getPlayerState(target);
		if (playerState == null) {
			CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
		}

		playerState.pauseChat();
		target.sendMessage(Component.text("Chat paused.", NamedTextColor.GRAY));
		return 1;
	}

	private static int unpause(CommandSender sender) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player)) {
			CommandAPI.fail("This command can only be run as a player.");
		}

		Player target = (Player) callee;
		PlayerState playerState = PlayerStateManager.getPlayerState(target);
		if (playerState == null) {
			CommandAPI.fail(callee.getName() + " has no chat state and must relog.");
		}

		target.sendMessage(Component.text("Unpausing chat.", NamedTextColor.GRAY));
		playerState.unpauseChat();
		return 1;
	}
}
