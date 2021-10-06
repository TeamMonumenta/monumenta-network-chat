package com.playmonumenta.networkchat.utils;

import com.playmonumenta.networkchat.NetworkChatProperties;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class CommandUtils {
	public static CommandSender getCallee(CommandSender sender) {
		if (sender instanceof ProxiedCommandSender) {
			return ((ProxiedCommandSender)sender).getCallee();
		}
		return sender;
	}

	public static CommandSender getCaller(CommandSender sender) {
		if (sender instanceof ProxiedCommandSender) {
			return ((ProxiedCommandSender)sender).getCaller();
		}
		return sender;
	}

	public static Boolean checkSudoCommand(CommandSender sender) {
		if (sender instanceof ProxiedCommandSender) {
			CommandSender callee = ((ProxiedCommandSender) sender).getCallee();
			CommandSender caller = ((ProxiedCommandSender) sender).getCaller();

			return callee.getName().equals(caller.getName()) || NetworkChatProperties.getSudoEnabled();
		}
		return true;
	}

	public static void fail(CommandSender sender, String message) throws WrapperCommandSyntaxException {
		if (sender instanceof ProxiedCommandSender) {
			CommandSender caller = ((ProxiedCommandSender) sender).getCaller();
			if (!sender.getName().equals(caller.getName())) {
				caller.sendMessage(Component.text(message, NamedTextColor.RED));
				CommandAPI.fail("");
			} else {
				CommandSender callee = ((ProxiedCommandSender) sender).getCallee();
				callee.sendMessage(Component.text(message, NamedTextColor.RED));
				CommandAPI.fail(message);
			}
		}
		CommandAPI.fail(message);
	}
}
