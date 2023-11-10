package com.playmonumenta.networkchat.inlinereplacements;

import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class ReplacementsManager {
	public List<InlineReplacement> mReplacements = new ArrayList<>();

	public ReplacementsManager() {
		mReplacements.add(new ItemHoverReplacement());
		// TODO Auto-generated constructor stub
	}

	public Component run(CommandSender sender, Component input) {
		MMLog.warning("RM: run()");
		for (InlineReplacement replacement : mReplacements) {
			MMLog.warning("RM: run() - replacement regex: " + replacement.mRegex);
			if (replacement.check(sender, MessagingUtils.plainText(input))) {
				MMLog.warning("RM: run() - replacement found");
				return replacement.replace(sender, input);
			}
		}
		return input;
	}
}
