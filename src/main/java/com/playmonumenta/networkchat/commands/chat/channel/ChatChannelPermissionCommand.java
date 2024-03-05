package com.playmonumenta.networkchat.commands.chat.channel;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.ChannelPredicate;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.channel.interfaces.ChannelPermissionNode;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.bukkit.command.CommandSender;

public class ChatChannelPermissionCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		for (String baseCommand : ChatCommand.COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new MultiLiteralArgument("permission"));
			arguments.add(ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_MANAGE
				.and(ChannelPredicate.INSTANCE_OF_PERMISSION_NODE)));
			arguments.add(new MultiLiteralArgument("get"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					return getChannelPermission(sender, (String) args[2]);
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("permission"));
				arguments.add(ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_MANAGE
					.and(ChannelPredicate.INSTANCE_OF_PERMISSION_NODE)));
				arguments.add(new MultiLiteralArgument("set"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executesNative((sender, args) -> {
						return changeChannelPerms(sender, (String) args[2], null);
					})
					.register();
			}

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("permission"));
				arguments.add(ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_MANAGE
					.and(ChannelPredicate.INSTANCE_OF_PERMISSION_NODE)));
				arguments.add(new MultiLiteralArgument("set"));
				arguments.add(new GreedyStringArgument("New channel perms"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executesNative((sender, args) -> {
						return changeChannelPerms(sender, (String) args[2], (String) args[4]);
					})
					.register();
			}
		}
	}

	private static int getChannelPermission(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		Channel channel = ChannelManager.getChannel(channelName);
		if (channel == null) {
			throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
		}

		if (!channel.mayManage(sender)) {
			throw CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
		}

		if (!(channel instanceof ChannelPermissionNode)) {
			throw CommandUtils.fail(sender, "This channel does not support permission");
		}

		String perms = ((ChannelPermissionNode) channel).getChannelPermission();

		if (perms == null || perms.isEmpty()) {
			sender.sendMessage("This channel has no permission set");
		} else {
			sender.sendMessage("Permission: " + perms + " for channel " + channelName);
		}

		return 1;
	}

	private static int changeChannelPerms(CommandSender sender, String channelName, @Nullable String newPerms) throws WrapperCommandSyntaxException {
		Channel channel = ChannelManager.getChannel(channelName);
		if (channel == null) {
			throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
		}

		if (!channel.mayManage(sender)) {
			throw CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
		}

		if (!(channel instanceof ChannelPermissionNode channelPermissionNode)) {
			throw CommandUtils.fail(sender, "This channel does not support permission");
		}

		channelPermissionNode.setChannelPermission(newPerms);
		ChannelManager.saveChannel(channel);
		return 1;
	}
}
