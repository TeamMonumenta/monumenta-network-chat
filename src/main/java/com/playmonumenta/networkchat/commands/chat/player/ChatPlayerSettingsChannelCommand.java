package com.playmonumenta.networkchat.commands.chat.player;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.ChannelPredicate;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.channel.property.ChannelSettings;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatPlayerSettingsChannelCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		for (String baseCommand : ChatCommand.COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_LISTEN));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						throw CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
						}

						String channelName = (String) args[3];
						Channel channel = ChannelManager.getChannel(channelName);
						if (channel == null) {
							throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}

						ChannelSettings settings = state.channelSettings(channel);
						return settings.commandFlag(sender, (String) args[4]);
					}
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_LISTEN));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagValues()));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						throw CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						if (CommandUtils.checkSudoCommandDisallowed(sender)) {
							throw CommandUtils.fail(sender, "You may not edit other player's settings on this shard.");
						}

						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
						}

						String channelName = (String) args[3];
						Channel channel = ChannelManager.getChannel(channelName);
						if (channel == null) {
							throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
						}

						ChannelSettings settings = state.channelSettings(channel);
						return settings.commandFlag(sender, (String) args[4], (String) args[5]);
					}
				})
				.register();
		}
	}
}
