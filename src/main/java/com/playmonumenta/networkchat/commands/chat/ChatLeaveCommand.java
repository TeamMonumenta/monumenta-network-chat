package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.ChannelPredicate;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatLeaveCommand {
	public static void register() {
		Argument<String> channelArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_LISTEN);

		CommandAPICommand leaveCommand = new CommandAPICommand("leave")
			.withArguments(channelArg)
			.executesNative((sender, args) -> {
				return leaveChannel(sender, args.getByArgument(channelArg));
			});
		leaveCommand.register();

		for (String baseCommand : ChatCommand.COMMANDS) {
			new CommandAPICommand(baseCommand).withSubcommand(leaveCommand).register();
		}
	}

	private static int leaveChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player target)) {
			throw CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			if (CommandUtils.checkSudoCommandDisallowed(sender)) {
				throw CommandUtils.fail(sender, "You may not make other players leave channels on this shard.");
			}

			PlayerState playerState = PlayerStateManager.getPlayerState(target);
			if (playerState == null) {
				throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
			}

			Channel channel = ChannelManager.getChannel(channelName);
			if (channel == null) {
				throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
			}

			playerState.leaveChannel(channel);
			target.sendMessage(Component.text("Left channel " + channelName + ".", NamedTextColor.GRAY));
			return 1;
		}
	}
}
