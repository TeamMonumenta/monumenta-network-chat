package com.playmonumenta.networkchat.commands.chat.channel;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.channel.property.ChannelAccess;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatChannelAccessCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		for (String baseCommand : ChatCommand.COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new MultiLiteralArgument("access"));
			arguments.add(new StringArgument("Channel Name")
				.replaceSuggestions(ChannelManager.SUGGESTIONS_MANAGEABLE_CHANNEL_NAMES));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument(ChannelAccess.getFlagKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					String channelName = (String) args[2];
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					ChannelAccess perms = channel.channelAccess();
					return perms.commandFlag(sender, (String) args[4]);
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("access"));
				arguments.add(new StringArgument("Channel Name")
					.replaceSuggestions(ChannelManager.SUGGESTIONS_MANAGEABLE_CHANNEL_NAMES));
				arguments.add(new MultiLiteralArgument("default"));
				arguments.add(new MultiLiteralArgument(ChannelAccess.getFlagKeys()));
				arguments.add(new MultiLiteralArgument(ChannelAccess.getFlagValues()));
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

						ChannelAccess perms = channel.channelAccess();
						int result = perms.commandFlag(sender, (String) args[4], (String) args[5]);
						ChannelManager.saveChannel(channel);
						return result;
					})
					.register();
			}

			arguments.clear();
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new MultiLiteralArgument("access"));
			arguments.add(new StringArgument("Channel Name")
				.replaceSuggestions(ChannelManager.SUGGESTIONS_MANAGEABLE_CHANNEL_NAMES));
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new StringArgument("name").replaceSuggestions(ChatCommand.ALL_CACHED_PLAYER_NAMES_SUGGESTIONS));
			arguments.add(new MultiLiteralArgument(ChannelAccess.getFlagKeys()));
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

					String playerName = (String) args[4];
					UUID playerId = MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
					if (playerId == null) {
						throw CommandUtils.fail(sender, "No such player " + playerName + ".");
					}
					ChannelAccess perms = channel.playerAccess(playerId);
					return perms.commandFlag(sender, (String) args[5]);
				})
				.register();

			if (NetworkChatProperties.getChatCommandModifyEnabled()) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("channel"));
				arguments.add(new MultiLiteralArgument("access"));
				arguments.add(new StringArgument("Channel Name")
					.replaceSuggestions(ChannelManager.SUGGESTIONS_MANAGEABLE_CHANNEL_NAMES));
				arguments.add(new MultiLiteralArgument("player"));
				arguments.add(new StringArgument("name").replaceSuggestions(ChatCommand.ALL_CACHED_PLAYER_NAMES_SUGGESTIONS));
				arguments.add(new MultiLiteralArgument(ChannelAccess.getFlagKeys()));
				arguments.add(new MultiLiteralArgument(ChannelAccess.getFlagValues()));
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

						String playerName = (String) args[4];
						UUID playerId = MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
						if (playerId == null) {
							throw CommandUtils.fail(sender, "No such player " + playerName + ".");
						}
						ChannelAccess perms = channel.playerAccess(playerId);
						int result = perms.commandFlag(sender, (String) args[5], (String) args[6]);
						if (perms.isDefault()) {
							channel.resetPlayerAccess(playerId);
						}
						ChannelManager.saveChannel(channel);
						return result;
					})
					.register();
			}
		}
	}
}
