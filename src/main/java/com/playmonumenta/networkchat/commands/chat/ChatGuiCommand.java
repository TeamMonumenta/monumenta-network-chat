package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.Message;
import com.playmonumenta.networkchat.MessageManager;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.custominventory.Gui;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ChatGuiCommand extends Gui {
	final String mBaseCommand;
	final Message mMessage;

	public ChatGuiCommand(Player player, String baseCommand, Message message) {
		super(player, 9, message.shownMessage(player));
		mBaseCommand = baseCommand;
		mMessage = message;
	}

	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		GreedyStringArgument idArg = new GreedyStringArgument("message ID");

		for (String baseCommand : ChatCommand.COMMANDS) {
			arguments.clear();
			arguments.add(new LiteralArgument("gui"));
			arguments.add(new LiteralArgument("message"));
			arguments.add(idArg);
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.gui.message")) {
						throw CommandUtils.fail(sender, "You do not have permission to run this command.");
					}

					messageGui(baseCommand, sender, args.getByArgument(idArg));
				})
				.register();
		}
	}

	private static void messageGui(String baseCommand, CommandSender sender, String messageIdStr) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player target)) {
			throw CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			PlayerState playerState = PlayerStateManager.getPlayerState(target);
			if (playerState == null) {
				throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
			}

			UUID messageId;
			try {
				messageId = UUID.fromString(messageIdStr);
			} catch (Exception e) {
				throw CommandUtils.fail(sender, "Invalid message ID. Click a channel name to open the message GUI.");
			}

			Message message = MessageManager.getMessage(messageId);
			if (message == null) {
				throw CommandUtils.fail(sender, "That message is no longer available on this shard. Pause chat and avoid switching shards to keep messages loaded.");
			}
			new ChatGuiCommand(target, baseCommand, message).open();
		}
	}

	@Override
	protected void setup() {
		if (mMessage.isDeleted()) {
			mPlayer.sendMessage(Component.text("That message has been deleted", NamedTextColor.RED));
			close();
			return;
		}
		ItemStack item;
		ItemMeta meta;

		Channel channel = mMessage.getChannel();
		if (channel != null) {
			item = new ItemStack(Material.OAK_DOOR);
			meta = item.getItemMeta();
			meta.displayName(Component.text("Leave channel", NamedTextColor.LIGHT_PURPLE)
				.decoration(TextDecoration.ITALIC, false));
			item.setItemMeta(meta);
			setItem(0, item)
				.onLeftClick(() -> {
					mPlayer.performCommand(mBaseCommand + " leave " + channel.getName());
					close();
				});

			item = new ItemStack(Material.REDSTONE);
			meta = item.getItemMeta();
			meta.displayName(Component.text("My channel settings", NamedTextColor.LIGHT_PURPLE)
				.decoration(TextDecoration.ITALIC, false));
			item.setItemMeta(meta);
			setItem(1, item)
				.onLeftClick(() -> {
					mPlayer.sendMessage(
						Component.text("[Edit your channel settings for " + channel.getName() + "]",
							NamedTextColor.LIGHT_PURPLE)
							.clickEvent(ClickEvent.suggestCommand("/" + mBaseCommand
								+ " player settings channel " + channel.getName() + " ")));
					close();
				});

			if (CommandUtils.hasPermission(mPlayer, "networkchat.rename")) {
				item = new ItemStack(Material.ANVIL);
				meta = item.getItemMeta();
				meta.displayName(Component.text("Rename channel", NamedTextColor.LIGHT_PURPLE)
					.decoration(TextDecoration.ITALIC, false));
				item.setItemMeta(meta);
				setItem(2, item)
					.onLeftClick(() -> {
						mPlayer.sendMessage(
							Component.text("[Rename channel " + channel.getName() + "]",
									NamedTextColor.LIGHT_PURPLE)
								.clickEvent(ClickEvent.suggestCommand("/" + mBaseCommand
									+ " channel rename " + channel.getName() + " ")));
						close();
					});
			}

			if (mMessage.senderIsPlayer() && channel.mayManage(mPlayer)) {
				String fromName = mMessage.getSenderName();

				item = new ItemStack(Material.IRON_DOOR);
				meta = item.getItemMeta();
				meta.displayName(Component.text("Sender channel access", NamedTextColor.LIGHT_PURPLE)
					.decoration(TextDecoration.ITALIC, false));
				item.setItemMeta(meta);
				setItem(3, item)
					.onLeftClick(() -> {
						mPlayer.sendMessage(
							Component.text("[Edit " + fromName + "'s access to channel " + channel.getName() + "]",
									NamedTextColor.LIGHT_PURPLE)
								.clickEvent(ClickEvent.suggestCommand("/" + mBaseCommand
									+ " channel access " + channel.getName() + " player " + fromName + " ")));
						close();
					});
			}
		}

		if (CommandUtils.hasPermission(mPlayer, "networkchat.delete.message")) {
			item = new ItemStack(Material.FLINT_AND_STEEL);
			meta = item.getItemMeta();
			meta.displayName(Component.text("Delete message", NamedTextColor.RED)
				.decoration(TextDecoration.ITALIC, false));
			item.setItemMeta(meta);
			setItem(7, item)
				.onLeftClick(() -> {
					mPlayer.performCommand(mBaseCommand
						+ " message delete " + mMessage.getUniqueId().toString());
					close();
				});
		}

		if (mMessage.senderIsPlayer()) {
			if (CommandUtils.hasPermission(mPlayer, "networkchat.message.deletefromsender")) {
				String fromName = mMessage.getSenderName();
				item = new ItemStack(Material.LAVA_BUCKET);
				meta = item.getItemMeta();
				meta.displayName(Component.text("Delete messages from " + fromName, NamedTextColor.RED)
					.decoration(TextDecoration.ITALIC, false));
				item.setItemMeta(meta);
				setItem(8, item)
					.onLeftClick(() -> {
						mPlayer.performCommand(mBaseCommand
							+ " message deletefromsender " + fromName);
						close();
					});
			}
		}
	}
}
