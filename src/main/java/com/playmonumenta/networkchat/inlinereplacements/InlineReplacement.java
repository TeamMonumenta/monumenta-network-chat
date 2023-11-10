package com.playmonumenta.networkchat.inlinereplacements;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.intellij.lang.annotations.RegExp;

public class InlineReplacement {

	@RegExp
	public String mRegex;

	//List of replacements, each of which is a pair of regex and a function that takes
	protected List<Pair<String, Function<Player, Component>>> mReplacements = new ArrayList<>();

	public boolean mRequirePlayer = false;

	public InlineReplacement(@RegExp String regex) {
		mRegex = regex;
	}

	public Component replace(CommandSender sender, Component input) {
		Component output = input;
		for (Pair<String, Function<Player, Component>> replacement : mReplacements) {
			output = output.replaceText(matcher -> {
				matcher.match(replacement.getLeft())
					.replacement(matchResult -> {
						Player player = (Player) sender;
						return replacement.getRight().apply(player);
					});
			});
		}
		return output;
	}

	public boolean check(CommandSender sender, String input) {
		if (mRequirePlayer && !(sender instanceof Player)) {
			return false;
		}
		return input.matches(".*" + mRegex + ".*");
	}

}
