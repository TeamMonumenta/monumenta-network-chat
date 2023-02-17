package com.playmonumenta.networkchat.channel;

import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.Message;
import com.playmonumenta.networkchat.MessageManager;
import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.channel.interfaces.ChannelAutoJoin;
import com.playmonumenta.networkchat.channel.interfaces.ChannelPermissionNode;
import com.playmonumenta.networkchat.channel.property.ChannelAccess;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.BooleanArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// A channel visible to all shards
public class ChannelGlobal extends Channel implements ChannelAutoJoin, ChannelPermissionNode {
	public static final String CHANNEL_CLASS_ID = "global";

	protected boolean mAutoJoin = true;
	private @Nullable String mChannelPermission = null;

	private ChannelGlobal(UUID channelId, Instant lastUpdate, String name) {
		super(channelId, lastUpdate, name);
	}

	public ChannelGlobal(String name) {
		this(UUID.randomUUID(), Instant.now(), name);
	}

	protected ChannelGlobal(JsonObject channelJson) throws Exception {
		super(channelJson);
		mAutoJoin = autoJoinFromJson(channelJson);
		mChannelPermission = permissionFromJson(channelJson);
	}

	@Override
	public JsonObject toJson() {
		JsonObject result = super.toJson();
		autoJoinToJson(result, mAutoJoin);
		permissionToJson(result, mChannelPermission);
		return result;
	}

	@Override
	public boolean defaultAutoJoinState() {
		return true;
	}

	@Override
	public boolean getAutoJoin() {
		return mAutoJoin;
	}

	@Override
	public void setAutoJoin(boolean autoJoin) {
		mAutoJoin = autoJoin;
	}

	public static void registerNewChannelCommands(String[] baseCommands, List<Argument<?>> prefixArguments) {
		List<Argument<?>> arguments;

		for (String baseCommand : baseCommands) {
			arguments = new ArrayList<>(prefixArguments);
			// last element of prefixArguments is channel ID
			arguments.add(new MultiLiteralArgument(CHANNEL_CLASS_ID));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.new.global")) {
						throw CommandUtils.fail(sender, "You do not have permission to create global channels.");
					}

					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelGlobal newChannel;

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelGlobal(channelName);
					} catch (Exception e) {
						throw CommandUtils.fail(sender, "Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
					}
					// Throws an exception if the channel already exists, failing the command.
					ChannelManager.registerNewChannel(sender, newChannel);
				})
				.register();

			arguments.add(new BooleanArgument("Auto Join"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.new.global")) {
						throw CommandUtils.fail(sender, "You do not have permission to create global channels.");
					}

					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelGlobal newChannel;

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelGlobal(channelName);
						newChannel.setAutoJoin((boolean)args[prefixArguments.size() + 1]);
					} catch (Exception e) {
						throw CommandUtils.fail(sender, "Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
					}
					// Throws an exception if the channel already exists, failing the command.
					ChannelManager.registerNewChannel(sender, newChannel);
				})
				.register();

			arguments.add(new GreedyStringArgument("Channel Permission"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.new.global")) {
						throw CommandUtils.fail(sender, "You do not have permission to create global channels.");
					}

					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelGlobal newChannel;

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelGlobal(channelName);
						newChannel.setAutoJoin((boolean)args[prefixArguments.size() + 1]);
						newChannel.mChannelPermission = (String)args[prefixArguments.size() + 2];
					} catch (Exception e) {
						throw CommandUtils.fail(sender, "Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
					}
					// Throws an exception if the channel already exists, failing the command.
					ChannelManager.registerNewChannel(sender, newChannel);
				})
				.register();
		}
	}

	@Override
	public String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	@Override
	public boolean shouldAutoJoin(PlayerState state) {
		Player player = state.getPlayer();
		return getAutoJoin() && player != null && mayListen(player);
	}

	@Override
	public @Nullable String getChannelPermission() {
		return mChannelPermission;
	}

	@Override
	public boolean hasPermission(CommandSender sender) {
		return mChannelPermission == null || sender.hasPermission(mChannelPermission);
	}

	@Override
	public void setChannelPermission(@Nullable String newPerm) {
		mChannelPermission = newPerm;
	}

	@Override
	public boolean mayChat(CommandSender sender) {
		if (!CommandUtils.hasPermission(sender, "networkchat.say.global")) {
			return false;
		}
		if (mChannelPermission != null && !CommandUtils.hasPermission(sender, mChannelPermission)) {
			return false;
		}

		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player player)) {
			return true;
		} else {
			ChannelAccess playerAccess = mPlayerAccess.get(player.getUniqueId());
			if (playerAccess == null) {
				return !Boolean.FALSE.equals(mDefaultAccess.mayChat());
			} else {
				return !Boolean.FALSE.equals(playerAccess.mayChat());
			}
		}
	}

	@Override
	public boolean mayListen(CommandSender sender) {
		if (!CommandUtils.hasPermission(sender, "networkchat.see.global")) {
			return false;
		}
		if (mChannelPermission != null && !CommandUtils.hasPermission(sender, mChannelPermission)) {
			return false;
		}

		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player player)) {
			return true;
		} else {
			UUID playerId = player.getUniqueId();

			ChannelAccess playerAccess = mPlayerAccess.get(playerId);
			if (playerAccess == null) {
				return !Boolean.FALSE.equals(mDefaultAccess.mayListen());
			} else {
				return !Boolean.FALSE.equals(playerAccess.mayListen());
			}
		}
	}

	@Override
	public void sendMessage(CommandSender sender, String messageText) throws WrapperCommandSyntaxException {
		if (!CommandUtils.hasPermission(sender, "networkchat.say.global")) {
			throw CommandUtils.fail(sender, "You do not have permission to talk in global chat.");
		}
		if (mChannelPermission != null && !CommandUtils.hasPermission(sender, mChannelPermission)) {
			throw CommandUtils.fail(sender, "You do not have permission to talk in " + mName + ".");
		}

		if (!mayChat(sender)) {
			throw CommandUtils.fail(sender, "You do not have permission to chat in this channel.");
		}

		WrapperCommandSyntaxException notListeningEx = isListeningCheck(sender);
		if (notListeningEx != null) {
			throw notListeningEx;
		}

		if (messageText.contains("@")) {
			if (messageText.contains("@everyone") && !CommandUtils.hasPermission(sender, "networkchat.ping.everyone")) {
				throw CommandUtils.fail(sender, "You do not have permission to ping everyone in this channel.");
			} else if (!CommandUtils.hasPermission(sender, "networkchat.ping.player") && MessagingUtils.containsPlayerMention(messageText)) {
				throw CommandUtils.fail(sender, "You do not have permission to ping a player in this channel.");
			}
		}

		@Nullable Message message = Message.createMessage(this, MessageType.CHAT, sender, null, messageText);
		if (message == null) {
			return;
		}

		try {
			MessageManager.getInstance().broadcastMessage(message);
		} catch (Exception e) {
			MMLog.warning("Could not send message; RabbitMQ is not responding.", e);
			throw CommandUtils.fail(sender, "Could not send message; RabbitMQ is not responding.");
		}
	}

	@Override
	public void distributeMessage(Message message) {
		showMessage(Bukkit.getConsoleSender(), message);
		for (Map.Entry<UUID, PlayerState> playerStateEntry : PlayerStateManager.getPlayerStates().entrySet()) {
			PlayerState state = playerStateEntry.getValue();
			Player player = state.getPlayer();
			if (player == null || !mayListen(player)) {
				continue;
			}

			if (state.isListening(this)) {
				// This accounts for players who have paused their chat
				state.receiveMessage(message);
			}
		}
	}

	@Override
	public Component shownMessage(CommandSender recipient, Message message) {
		TextColor channelColor;
		if (mMessageColor != null) {
			channelColor = mMessageColor;
		} else {
			channelColor = NetworkChatPlugin.messageColor(CHANNEL_CLASS_ID);
		}
		String prefix = NetworkChatPlugin.messageFormat(CHANNEL_CLASS_ID);
		if (prefix == null) {
			prefix = "";
		}
		prefix = prefix
			.replace("<message_gui_cmd>", message.getGuiCommand())
			.replace("<channel_color>", MessagingUtils.colorToMiniMessage(channelColor)) + " ";

		return Component.empty()
			.append(MessagingUtils.SENDER_FMT_MINIMESSAGE.deserialize(prefix,
				Placeholder.unparsed("channel_name", mName),
				Placeholder.component("sender", message.getSenderComponent())))
			.append(Component.empty().color(channelColor).append(message.getMessage()));
	}

	@Override
	public void showMessage(CommandSender recipient, Message message) {
		UUID senderUuid = message.getSenderId();
		recipient.sendMessage(message.getSenderIdentity(), shownMessage(recipient, message), message.getMessageType());
		if (recipient instanceof Player player && !player.getUniqueId().equals(senderUuid)) {
			@Nullable PlayerState playerState = PlayerStateManager.getPlayerState(player);
			if (playerState == null) {
				player.sendMessage(MessagingUtils.noChatState(player));
				return;
			}
			playerState.playMessageSound(message);
		}
	}
}