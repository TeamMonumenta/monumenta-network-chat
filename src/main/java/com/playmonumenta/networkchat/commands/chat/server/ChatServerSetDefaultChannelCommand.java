package com.playmonumenta.networkchat.commands.chat.server;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.DefaultChannels;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.channel.ChannelWorld;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import java.util.ArrayList;
import java.util.List;

public class ChatServerSetDefaultChannelCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		for (String baseCommand : ChatCommand.COMMANDS) {
			for (String channelType : DefaultChannels.CHANNEL_TYPES) {
				if (NetworkChatProperties.getChatCommandModifyEnabled()) {
					arguments.clear();
					arguments.add(new MultiLiteralArgument("server"));
					arguments.add(new MultiLiteralArgument("setdefaultchannel"));
					arguments.add(new MultiLiteralArgument(channelType));
					new CommandAPICommand(baseCommand)
						.withArguments(arguments)
						.executesNative((sender, args) -> {
							if (!CommandUtils.hasPermission(sender, "networkchat.setdefaultchannel")) {
								throw CommandUtils.fail(sender, "You do not have permission to change server-wide default channels.");
							}

							return ChannelManager.getDefaultChannels().command(sender, channelType, true);
						})
						.register();

					arguments.clear();
					arguments.add(new MultiLiteralArgument("server"));
					arguments.add(new MultiLiteralArgument("setdefaultchannel"));
					arguments.add(new MultiLiteralArgument(channelType));
					if (channelType.equals("default")) {
						arguments.add(new StringArgument("channel name").replaceSuggestions(ChannelManager.SUGGESTIONS_CHANNEL_NAMES));
					} else if (channelType.equals(DefaultChannels.WORLD_CHANNEL)) {
						arguments.add(new StringArgument("channel name")
							.replaceSuggestions(ChannelManager.getChannelNameSuggestions(ChannelWorld.CHANNEL_CLASS_ID)));
					} else {
						arguments.add(new StringArgument("channel name").replaceSuggestions(ChannelManager.getChannelNameSuggestions(channelType)));
					}
					new CommandAPICommand(baseCommand)
						.withArguments(arguments)
						.executesNative((sender, args) -> {
							if (!CommandUtils.hasPermission(sender, "networkchat.setdefaultchannel")) {
								throw CommandUtils.fail(sender, "You do not have permission to change server-wide default channels.");
							}

							int result = ChannelManager.getDefaultChannels().command(sender, channelType, (String) args[3]);
							ChannelManager.saveDefaultChannels();
							return result;
						})
						.register();
				}
			}
		}
	}
}
