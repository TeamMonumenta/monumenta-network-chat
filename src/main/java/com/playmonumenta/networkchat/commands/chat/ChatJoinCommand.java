package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatJoinCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();
		arguments.add(new MultiLiteralArgument("join"));
		arguments.add(new StringArgument("Channel Name").replaceSuggestions(ChannelManager.SUGGESTIONS_LISTENABLE_CHANNEL_NAMES));

		for (String baseCommand : ChatCommand.COMMANDS) {
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					return joinChannel(sender, (String) args[1]);
				})
				.register();
		}

		arguments.clear();
		arguments.add(new StringArgument("Channel Name").replaceSuggestions(ChannelManager.SUGGESTIONS_LISTENABLE_CHANNEL_NAMES));
		new CommandAPICommand("join")
			.withArguments(arguments)
			.executesNative((sender, args) -> {
				return joinChannel(sender, (String) args[0]);
			})
			.register();
	}

	private static int joinChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player target)) {
			throw CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			if (CommandUtils.checkSudoCommandDisallowed(sender)) {
				throw CommandUtils.fail(sender, "You may not make other players join channels on this shard.");
			}

			PlayerState playerState = PlayerStateManager.getPlayerState(target);
			if (playerState == null) {
				throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
			}

			Channel channel = ChannelManager.getChannel(channelName);
			if (channel == null) {
				throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
			}

			playerState.setActiveChannel(channel);
			target.sendMessage(Component.text("Joined channel " + channelName + ".", NamedTextColor.GRAY));
			return 1;
		}
	}
}
