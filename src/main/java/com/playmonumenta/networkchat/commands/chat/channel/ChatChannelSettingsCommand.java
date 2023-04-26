package com.playmonumenta.networkchat.commands.chat.channel;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.channel.property.ChannelSettings;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.FloatArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.SoundArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Sound;

public class ChatChannelSettingsCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		for (String baseCommand : ChatCommand.COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(ChannelManager.SUGGESTIONS_LISTENABLE_CHANNEL_NAMES));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					String channelName = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					ChannelSettings settings = channel.channelSettings();
					return settings.commandFlag(sender, (String) args[3]);
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("settings"));
				arguments.add(new StringArgument("Channel Name").replaceSuggestions(ChannelManager.SUGGESTIONS_LISTENABLE_CHANNEL_NAMES));
				arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
				arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagValues()));
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

						ChannelSettings settings = channel.channelSettings();
						int result = settings.commandFlag(sender, (String) args[3], (String) args[4]);
						ChannelManager.saveChannel(channel);
						return result;
					})
					.register();
			}

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("settings"));
				arguments.add(new StringArgument("Channel Name").replaceSuggestions(ChannelManager.SUGGESTIONS_LISTENABLE_CHANNEL_NAMES));
				arguments.add(new MultiLiteralArgument("sound"));
				arguments.add(new MultiLiteralArgument("add"));
				arguments.add(new SoundArgument("Notification sound"));
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

						ChannelSettings settings = channel.channelSettings();
						settings.addSound((Sound) args[5], 1, 1);
						ChannelManager.saveChannel(channel);

						return 1;
					})
					.register();
			}

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.add(new FloatArgument("Volume", 0.0f, 1.0f));
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

						ChannelSettings settings = channel.channelSettings();
						settings.addSound((Sound) args[5], (Float) args[6], 1);
						ChannelManager.saveChannel(channel);

						return 1;
					})
					.register();
			}

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.add(new FloatArgument("Pitch", 0.5f, 2.0f));
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

						ChannelSettings settings = channel.channelSettings();
						settings.addSound((Sound) args[5], (Float) args[6], (Float) args[7]);
						ChannelManager.saveChannel(channel);

						return 1;
					})
					.register();
			}

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("settings"));
				arguments.add(new StringArgument("Channel Name").replaceSuggestions(ChannelManager.SUGGESTIONS_LISTENABLE_CHANNEL_NAMES));
				arguments.add(new MultiLiteralArgument("sound"));
				arguments.add(new MultiLiteralArgument("clear"));
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

						ChannelSettings settings = channel.channelSettings();
						settings.clearSound();
						ChannelManager.saveChannel(channel);

						return 1;
					})
					.register();
			}
		}
	}
}
