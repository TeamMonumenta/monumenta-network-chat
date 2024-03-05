package com.playmonumenta.networkchat.tagresolvers;

import com.playmonumenta.networkchat.utils.MessagingUtils;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;

public class OptSpace {
	public static TagResolver optAppendSpace() {
		return TagResolver.resolver("OptAppendSpace", (ArgumentQueue args, Context context) -> {
			Audience audience = Bukkit.getServer();
			audience.sendMessage(Component.text("[OptAppendSpace] Start", NamedTextColor.GOLD));
			String contents = args.popOr("Expected minimessage value").value();
			Component component = context.deserialize(contents);
			audience.sendMessage(Component.empty()
				.append(Component.text("[OptAppendSpace] Inner arg: \"", NamedTextColor.GOLD))
				.append(component)
				.append(Component.text("\"", NamedTextColor.GOLD)));
			if (MessagingUtils.plainText(component).isEmpty()) {
				audience.sendMessage(Component.text("[OptAppendSpace] Result: empty", NamedTextColor.GOLD));
				return Tag.selfClosingInserting(Component.empty());
			}
			audience.sendMessage(Component.text("[OptAppendSpace] Result: append space", NamedTextColor.GOLD));
			return Tag.selfClosingInserting(component.append(Component.space()));
		});
	}

	public static TagResolver optPrependSpace() {
		return TagResolver.resolver("OptPrependSpace", (ArgumentQueue args, Context context) -> {
			String contents = args.popOr("Expected minimessage value").value();
			Component component = context.deserialize(contents);
			if (MessagingUtils.plainText(component).isEmpty()) {
				return Tag.selfClosingInserting(Component.empty());
			}
			return Tag.selfClosingInserting(Component.space().append(component));
		});
	}
}
