package com.playmonumenta.networkchat.inlinereplacements;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.intellij.lang.annotations.RegExp;

public class ItemHoverReplacement extends InlineReplacement {

	private static final List<Material> BANNED_MATERIALS = new ArrayList<>(
		List.of(Material.SHULKER_BOX, Material.BLACK_SHULKER_BOX,
			Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX,
			Material.CYAN_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
			Material.GREEN_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX,
			Material.LIGHT_GRAY_SHULKER_BOX, Material.LIME_SHULKER_BOX,
			Material.MAGENTA_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
			Material.PINK_SHULKER_BOX, Material.PURPLE_SHULKER_BOX,
			Material.RED_SHULKER_BOX, Material.WHITE_SHULKER_BOX,
			Material.YELLOW_SHULKER_BOX, Material.WRITTEN_BOOK)
	);

	@RegExp
	private static final String mMainhandRegex = "<mainhand>";
	@RegExp
	private static final String mOffhandRegex = "<offhand>";

	public ItemHoverReplacement() {
		super("Item Hover",
			"(?<=^|[^\\\\])<(mainhand|offhand)>",
			"itemhover");
		mReplacements.add(Pair.of(mMainhandRegex, sender -> {
			if (sender instanceof Player player
				&& !BANNED_MATERIALS.contains(player.getInventory().getItemInMainHand().getType())) {
				ItemStack item = player.getInventory().getItemInMainHand();
				return item.displayName().hoverEvent(item);
			}
			return Component.text("<mainhand>");
		}));
		mReplacements.add(Pair.of(mOffhandRegex, sender -> {
			if (sender instanceof Player player
				&& !BANNED_MATERIALS.contains(player.getInventory().getItemInOffHand().getType())) {
				ItemStack item = player.getInventory().getItemInOffHand();
				return item.displayName().hoverEvent(item);
			}
			return Component.text("<offhand>");
		}));
	}
}
