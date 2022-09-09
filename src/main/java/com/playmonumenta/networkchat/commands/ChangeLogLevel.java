package com.playmonumenta.networkchat.commands;

import com.playmonumenta.networkchat.utils.MMLog;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import java.util.logging.Level;

public class ChangeLogLevel {
	public static final String[] COMMANDS = new String[]{"chat", "ch", "networkchat"};

	public static void register() {
		for (String baseCommand : COMMANDS) {
		    new CommandAPICommand(baseCommand)
			    .withSubcommand(new CommandAPICommand("changeloglevel")
				    .withPermission(CommandPermission.fromString("networkchat.changeloglevel"))
				    .withSubcommand(new CommandAPICommand("INFO")
					    .executes((sender, args) -> {
						    MMLog.setLevel(Level.INFO);
					    }))
				    .withSubcommand(new CommandAPICommand("FINE")
					    .executes((sender, args) -> {
						    MMLog.setLevel(Level.FINE);
					    }))
				    .withSubcommand(new CommandAPICommand("FINER")
					    .executes((sender, args) -> {
						    MMLog.setLevel(Level.FINER);
					    }))
				    .withSubcommand(new CommandAPICommand("FINEST")
					    .executes((sender, args) -> {
						    MMLog.setLevel(Level.FINEST);
					    }))
		    ).register();
		}
	}
}
