package com.playmonumenta.networkchat.utils;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;

public class CommandUtils {
	public static CommandSender getCallee(CommandSender sender) {
		if (sender instanceof ProxiedCommandSender) {
			return ((ProxiedCommandSender)sender).getCallee();
		}
		return sender;
	}
}
