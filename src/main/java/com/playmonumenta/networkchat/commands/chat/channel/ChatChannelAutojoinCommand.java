package com.playmonumenta.networkchat.commands.chat.channel;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.channel.interfaces.ChannelAutoJoin;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import java.util.ArrayList;
import java.util.List;

public class ChatChannelAutojoinCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		for (String baseCommand : ChatCommand.COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new MultiLiteralArgument("autojoin"));
			arguments.add(new StringArgument("channel name").replaceSuggestions(ChannelManager.SUGGESTIONS_AUTO_JOINABLE_CHANNEL_NAMES));
			arguments.add(new MultiLiteralArgument("get"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					String channelName = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelName);

					if (channel == null) {
						throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					if (!channel.mayManage(sender)) {
						throw CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
					}

					if (!(channel instanceof ChannelAutoJoin)) {
						throw CommandUtils.fail(sender, "This channel has no auto join setting.");
					}

					sender.sendMessage("Channel " + channelName + " auto join: " + (((ChannelAutoJoin) channel).getAutoJoin() ? "enabled." : "disabled."));

					return 1;
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("autojoin"));
				arguments.add(new StringArgument("channel name").replaceSuggestions(ChannelManager.SUGGESTIONS_AUTO_JOINABLE_CHANNEL_NAMES));
				arguments.add(new MultiLiteralArgument("enable", "disable"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executesNative((sender, args) -> {
						String channelName = (String) args[2];
						Channel channel = ChannelManager.getChannel(channelName);

						if (channel == null) {
							throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}

						if (!channel.mayManage(sender)) {
							throw CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
						}

						if (!(channel instanceof ChannelAutoJoin)) {
							throw CommandUtils.fail(sender, "This channel does not support auto join settings.");
						}

						boolean newAutoJoin = args[3].equals("enable");

						((ChannelAutoJoin) channel).setAutoJoin(newAutoJoin);
						ChannelManager.saveChannel(channel);

						sender.sendMessage("Channel " + channelName + " set auto join to " + (newAutoJoin ? "enabled." : "disabled."));

						return 1;
					})
					.register();
			}
		}
	}
}
