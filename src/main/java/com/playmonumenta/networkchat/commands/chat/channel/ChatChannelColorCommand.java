package com.playmonumenta.networkchat.commands.chat.channel;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public class ChatChannelColorCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		for (String baseCommand : ChatCommand.COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new MultiLiteralArgument("color"));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(ChannelManager.SUGGESTIONS_CHATABLE_CHANNEL_NAMES));
			arguments.add(new MultiLiteralArgument("get"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					String channelName = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelName);

					if (channel == null) {
						throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					@Nullable TextColor color = channel.color();
					sender.sendMessage(Component.text(channelName + " is " + MessagingUtils.colorToString(color), color));
					return 1;
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("color"));
				arguments.add(new StringArgument("Channel Name").replaceSuggestions(ChannelManager.SUGGESTIONS_CHATABLE_CHANNEL_NAMES));
				arguments.add(new MultiLiteralArgument("clear"));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executesNative((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.channel.color")) {
							throw CommandUtils.fail(sender, "You do not have permission to change channel colors.");
						}

						String channelName = (String) args[2];
						Channel channel = ChannelManager.getChannel(channelName);

						if (channel == null) {
							throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}

						if (!channel.mayManage(sender)) {
							throw CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
						}

						TextColor color = NetworkChatPlugin.messageColor(channel.getClassId());
						channel.color(sender, null);
						ChannelManager.saveChannel(channel);
						sender.sendMessage(Component.text(channelName + " reset to.", color));
						return 1;
					})
					.register();
			}

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("color"));
				arguments.add(new StringArgument("Channel Name").replaceSuggestions(ChannelManager.SUGGESTIONS_CHATABLE_CHANNEL_NAMES));
				arguments.add(new MultiLiteralArgument("set"));
				arguments.add(new GreedyStringArgument("color").replaceSuggestions(ChatCommand.COLOR_SUGGESTIONS));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executesNative((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.channel.color")) {
							throw CommandUtils.fail(sender, "You do not have permission to change channel colors.");
						}

						String channelName = (String) args[2];
						Channel channel = ChannelManager.getChannel(channelName);

						if (channel == null) {
							throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}

						if (!channel.mayManage(sender)) {
							throw CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
						}

						String colorString = (String) args[4];
						@Nullable TextColor color = MessagingUtils.colorFromString(colorString);
						if (color == null) {
							throw CommandUtils.fail(sender, "No such color " + colorString);
						}
						channel.color(sender, color);
						ChannelManager.saveChannel(channel);
						sender.sendMessage(Component.text(channelName + " set to " + MessagingUtils.colorToString(color), color));
						return 1;
					})
					.register();
			}
		}
	}
}
