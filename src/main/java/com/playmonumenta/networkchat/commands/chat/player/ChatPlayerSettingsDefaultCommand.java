package com.playmonumenta.networkchat.commands.chat.player;

import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
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

public class ChatPlayerSettingsDefaultCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		for (String baseCommand : ChatCommand.COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("default"));
			arguments.add(new MultiLiteralArgument(ChannelSettings.getFlagKeys()));
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
						ChannelSettings settings = state.channelSettings();
						return settings.commandFlag(sender, (String) args[3]);
					}
				})
				.register();

			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("settings"));
			arguments.add(new MultiLiteralArgument("default"));
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
						ChannelSettings settings = state.channelSettings();
						return settings.commandFlag(sender, (String) args[3], (String) args[4]);
					}
				})
				.register();
		}
	}
}
