package com.playmonumenta.networkchat.commands.chat.player;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.DefaultChannels;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.channel.ChannelWorld;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatPlayerSetDefaultChannelCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		for (String baseCommand : ChatCommand.COMMANDS) {
			for (String channelType : DefaultChannels.CHANNEL_TYPES) {
				arguments.clear();
				arguments.add(new MultiLiteralArgument("player"));
				arguments.add(new MultiLiteralArgument("setdefaultchannel"));
				arguments.add(new MultiLiteralArgument(channelType));
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executesNative((sender, args) -> {
						CommandSender callee = CommandUtils.getCallee(sender);
						if (!(callee instanceof Player target)) {
							throw CommandUtils.fail(sender, "This command can only be run as a player.");
						} else {
							if (CommandUtils.checkSudoCommandDisallowed(sender)) {
								throw CommandUtils.fail(sender, "You may not change other players' default channel on this shard.");
							}

							PlayerState state = PlayerStateManager.getPlayerState(target);
							if (state == null) {
								throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
							}

							return state.defaultChannels().command(sender, channelType);
						}
					})
					.register();

				arguments.clear();
				arguments.add(new MultiLiteralArgument("player"));
				arguments.add(new MultiLiteralArgument("setdefaultchannel"));
				arguments.add(new MultiLiteralArgument(channelType));
				if (channelType.equals("default") || channelType.equals(DefaultChannels.GUILD_CHANNEL)) {
					arguments.add(new StringArgument("channel name").replaceSuggestions(ChannelManager.SUGGESTIONS_CHATABLE_CHANNEL_NAMES));
				} else if (channelType.equals(DefaultChannels.WORLD_CHANNEL)) {
					arguments.add(new StringArgument("channel name").replaceSuggestions(ChannelManager.getChannelNameSuggestions(ChannelWorld.CHANNEL_CLASS_ID)));
				} else {
					arguments.add(new StringArgument("channel name").replaceSuggestions(ChannelManager.getChannelNameSuggestions(channelType)));
				}
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executesNative((sender, args) -> {
						CommandSender callee = CommandUtils.getCallee(sender);
						if (!(callee instanceof Player target)) {
							throw CommandUtils.fail(sender, "This command can only be run as a player.");
						} else {
							if (CommandUtils.checkSudoCommandDisallowed(sender)) {
								throw CommandUtils.fail(sender, "You may not change other players' default channel on this shard.");
							}

							PlayerState state = PlayerStateManager.getPlayerState(target);
							if (state == null) {
								throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
							}
							return state.defaultChannels().command(sender, channelType, (String) args[3]);
						}
					})
					.register();
			}
		}
	}
}
