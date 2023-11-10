package com.playmonumenta.networkchat.inlinereplacements;

import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.inventory.ItemStack;
import org.intellij.lang.annotations.RegExp;

public class ItemHoverReplacement extends InlineReplacement {

	@RegExp
	private static final String mMainhandRegex = "<mainhand>";
	@RegExp
	private static final String mOffhandRegex = "<offhand>";

	public ItemHoverReplacement() {
		super("<(mainhand|offhand)>");
		mRequirePlayer = true;
		mReplacements.add(Pair.of(mMainhandRegex, player -> {
			ItemStack item = player.getInventory().getItemInMainHand();
			return item.displayName().hoverEvent(item);
		}));
		mReplacements.add(Pair.of(mOffhandRegex, player -> {
			ItemStack item = player.getInventory().getItemInOffHand();
			return item.displayName().hoverEvent(item);
		}));
	}
}
