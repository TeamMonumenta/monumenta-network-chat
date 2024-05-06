package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.RemotePlayerManager;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import java.util.ArrayList;
import java.util.List;

public class ChatListPlayersCommand {
	public static void register() {
		for (String baseCommand : ChatCommand.COMMANDS) {
			new CommandAPICommand(baseCommand)
				.withArguments(new LiteralArgument("listplayers"))
				.withPermission("networkchat.listplayers")
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.listplayers")) {
						throw CommandUtils.fail(sender, "You do not have permission to run this command.");
					}

					RemotePlayerManager.showOnlinePlayers(CommandUtils.getCallee(sender));
					return 1;
				})
				.register();
		}
	}
}
