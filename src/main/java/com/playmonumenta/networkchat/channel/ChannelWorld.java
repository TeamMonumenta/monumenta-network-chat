package com.playmonumenta.networkchat.channel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.Message;
import com.playmonumenta.networkchat.MessageManager;
import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.RemotePlayerManager;
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
import dev.jorel.commandapi.wrappers.NativeProxyCommandSender;
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
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

// A channel visible only to this world (and moderators who opt in from elsewhere)
public class ChannelWorld extends Channel implements ChannelAutoJoin, ChannelPermissionNode {
	public static final String CHANNEL_CLASS_ID = "world";

	protected boolean mAutoJoin = true;
	private @Nullable String mChannelPermission = null;

	private ChannelWorld(UUID channelId, Instant lastUpdate, String name) {
		super(channelId, lastUpdate, name);
	}

	public ChannelWorld(String name) {
		this(UUID.randomUUID(), Instant.now(), name);
	}

	protected ChannelWorld(JsonObject channelJson) throws Exception {
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
					if (!CommandUtils.hasPermission(sender, "networkchat.new.world")) {
						throw CommandUtils.fail(sender, "You do not have permission to create world channels.");
					}

					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelWorld newChannel;

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelWorld(channelName);
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
					if (!CommandUtils.hasPermission(sender, "networkchat.new.world")) {
						throw CommandUtils.fail(sender, "You do not have permission to create world channels.");
					}

					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelWorld newChannel;

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelWorld(channelName);
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
					if (!CommandUtils.hasPermission(sender, "networkchat.new.world")) {
						throw CommandUtils.fail(sender, "You do not have permission to create world channels.");
					}

					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelWorld newChannel;

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelWorld(channelName);
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
		if (!CommandUtils.hasPermission(sender, "networkchat.say.world")) {
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
		if (!CommandUtils.hasPermission(sender, "networkchat.see.world")) {
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
		if (!CommandUtils.hasPermission(sender, "networkchat.say.world")) {
			throw CommandUtils.fail(sender, "You do not have permission to talk in world chat.");
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

		World world;
		if (sender instanceof NativeProxyCommandSender nativeSender) {
			world = nativeSender.getWorld();
		} else if (sender instanceof Entity entity) {
			world = entity.getWorld();
		} else if (sender instanceof BlockCommandSender blockCommandSender) {
			world = blockCommandSender.getBlock().getWorld();
		} else {
			MMLog.warning("Unable to get world for world channel message for sender " + sender);
			throw CommandUtils.fail(sender, "Unable to get world for world channel message.");
		}

		JsonObject extraData = new JsonObject();
		extraData.addProperty("fromShard", RemotePlayerManager.getShardName());
		extraData.addProperty("fromWorld", world.getName());

		@Nullable Message message = Message.createMessage(this, MessageType.CHAT, sender, extraData, messageText);
		if (message == null) {
			return;
		}

		MessageManager.getInstance().broadcastMessage(message);
	}

	@Override
	public void distributeMessage(Message message) {
		JsonObject extraData = message.getExtraData();
		if (extraData == null) {
			MMLog.warning("Got world chat message with no fromShard, ignoring.");
			return;
		}

		String fromShard;
		JsonElement fromShardJsonElement = extraData.get("fromShard");
		if (!fromShardJsonElement.isJsonPrimitive()) {
			MMLog.warning("Got world chat message with invalid fromShard json, ignoring.");
			return;
		}
		JsonPrimitive fromShardJsonPrimitive = fromShardJsonElement.getAsJsonPrimitive();
		if (!fromShardJsonPrimitive.isString()) {
			MMLog.warning("Got world chat message with invalid fromShard json, ignoring.");
			return;
		}
		fromShard = fromShardJsonPrimitive.getAsString();
		if (!fromShard.equals(RemotePlayerManager.getShardName())) {
			// TODO Chat spy here
			return;
		}

		String fromWorld;
		JsonElement fromWorldJsonElement = extraData.get("fromWorld");
		if (!fromWorldJsonElement.isJsonPrimitive()) {
			MMLog.warning("Got world chat message with invalid fromWorld json, ignoring.");
			return;
		}
		JsonPrimitive fromWorldJsonPrimitive = fromWorldJsonElement.getAsJsonPrimitive();
		if (!fromWorldJsonPrimitive.isString()) {
			MMLog.warning("Got world chat message with invalid fromWorld json, ignoring.");
			return;
		}
		fromWorld = fromWorldJsonPrimitive.getAsString();

		showMessage(Bukkit.getConsoleSender(), message);
		for (Map.Entry<UUID, PlayerState> playerStateEntry : PlayerStateManager.getPlayerStates().entrySet()) {
			PlayerState state = playerStateEntry.getValue();
			Player player = state.getPlayer();
			if (player == null || !player.getWorld().getName().equals(fromWorld) || !mayListen(player)) {
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
