package com.playmonumenta.networkchat.inlinereplacements;

import java.util.ArrayList;
import java.util.List;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;


public class ReplacementsManager {
	public List<InlineReplacement> mReplacements = new ArrayList<>();

	public ReplacementsManager() {
		mReplacements.add(new ItemHoverReplacement());
		// TODO Auto-generated constructor stub
	}

	public Component run(CommandSender sender, Component input) {
		for (InlineReplacement replacement : mReplacements) {
			if (replacement.check(sender, MessagingUtils.plainText(input))) {
				return replacement.replace(sender, input);
			}
		}
		return input;
	}
}
