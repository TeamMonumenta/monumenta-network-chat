package com.playmonumenta.networkchat.commands.chat.channel;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.utils.CommandUtils;
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

public class ChatChannelDeleteCommand {
	public static void register() {
		if (NetworkChatProperties.getChatCommandModifyEnabled()) {
			List<Argument<?>> arguments = new ArrayList<>();
			arguments.add(new MultiLiteralArgument("channel"));
			arguments.add(new MultiLiteralArgument("delete"));
			arguments.add(new StringArgument("Channel Name").replaceSuggestions(ChannelManager.SUGGESTIONS_CHATABLE_CHANNEL_NAMES));

			for (String baseCommand : ChatCommand.COMMANDS) {
				new CommandAPICommand(baseCommand)
					.withArguments(arguments)
					.executesNative((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.delete.channel")) {
							throw CommandUtils.fail(sender, "You do not have permission to delete channels.");
						}

						return deleteChannel(sender, (String) args[2]);
					})
					.register();
			}
		}
	}

	private static int deleteChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		// May call throw CommandUtils.fail(sender, )
		ChannelManager.deleteChannel(channelName);
		sender.sendMessage(Component.text("Channel " + channelName + " deleted.", NamedTextColor.GRAY));
		return 1;
	}
}
