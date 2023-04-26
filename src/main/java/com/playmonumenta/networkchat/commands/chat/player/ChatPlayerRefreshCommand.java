package com.playmonumenta.networkchat.commands.chat.player;

import com.playmonumenta.networkchat.RemotePlayerManager;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatPlayerRefreshCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		for (String baseCommand : ChatCommand.COMMANDS) {
			arguments.clear();
			arguments.add(new MultiLiteralArgument("player"));
			arguments.add(new MultiLiteralArgument("refresh"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						throw CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						RemotePlayerManager.refreshLocalPlayer(target);
					}
					return 1;
				})
				.register();

			arguments.add(new EntitySelectorArgument.ManyPlayers("Players"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					@SuppressWarnings("unchecked")
					Collection<Player> players = (Collection<Player>) args[2];

					for (Player player : players) {
						RemotePlayerManager.refreshLocalPlayer(player);
					}
					return 1;
				})
				.register();
		}
	}
}
