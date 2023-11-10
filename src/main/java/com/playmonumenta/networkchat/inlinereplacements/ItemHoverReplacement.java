package com.playmonumenta.networkchat.inlinereplacements;

import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.intellij.lang.annotations.RegExp;

public class ItemHoverReplacement extends InlineReplacement {

	@RegExp
	private static final String mMainhandRegex = "<mainhand>";
	@RegExp
	private static final String mOffhandRegex = "<offhand>";

	public ItemHoverReplacement() {
		super("Item Hover",
			"<(mainhand|offhand)>",
			"itemhover");
		mReplacements.add(Pair.of(mMainhandRegex, sender -> {
			if (sender instanceof Player player) {
				ItemStack item = player.getInventory().getItemInMainHand();
				return item.displayName().hoverEvent(item);
			}
			return Component.text("<mainhand>");
		}));
		mReplacements.add(Pair.of(mOffhandRegex, sender -> {
			if (sender instanceof Player player) {
				ItemStack item = player.getInventory().getItemInOffHand();
				return item.displayName().hoverEvent(item);
			}
			return Component.text("<offhand>");
		}));
	}
}
