package com.playmonumenta.networkchat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.NetworkRelayAPI;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

import org.bukkit.command.CommandSender;

import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.transformation.TransformationType;
import net.kyori.adventure.text.minimessage.markdown.DiscordFlavor;

// A channel visible only to this shard (and moderators who opt in from elsewhere)
public class ChannelLocal extends ChannelBase {
	public static final String CHANNEL_CLASS_ID = "local";

	private UUID mId;
	private String mShardName;
	private String mName;

	private ChannelLocal(UUID channelId, String shardName, String name) {
		mId = channelId;
		mShardName = shardName;
		mName = name;
	}

	public ChannelLocal(String name) throws Exception {
		mId = UUID.randomUUID();
		mShardName = NetworkRelayAPI.getShardName();
		mName = name;
	}

	protected static ChannelBase fromJsonInternal(JsonObject channelJson) throws Exception {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();
		if (channelClassId == null || !channelClassId.equals(CHANNEL_CLASS_ID)) {
			throw new Exception("Cannot create ChannelLocal from channel ID " + channelClassId);
		}
		String uuidString = channelJson.getAsJsonPrimitive("uuid").getAsString();
		UUID channelId = UUID.fromString(uuidString);
		String shardName = channelJson.getAsJsonPrimitive("shardName").getAsString();
		String name = channelJson.getAsJsonPrimitive("name").getAsString();
		return new ChannelLocal(channelId, shardName, name);
	}

	public static void registerNewChannelCommands(String[] baseCommands, List<Argument> prefixArguments) {
		List<Argument> arguments;

		for (String baseCommand : baseCommands) {
			arguments = new ArrayList<>(prefixArguments);
			// last element of prefixArguments is channel ID
			arguments.add(new MultiLiteralArgument(CHANNEL_CLASS_ID));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executes((sender, args) -> {
					String channelName = (String)args[prefixArguments.size()-1];
					ChannelLocal newChannel = null;
					// TODO Perms check

					// Ignore [prefixArguments.size()] ID vs shorthand, they both mean the same thing.
					try {
						newChannel = new ChannelLocal(channelName);
					} catch (Exception e) {
						CommandAPI.fail("Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
					}
					// Throws an exception if the channel already exists, failing the command.
					ChannelManager.registerNewChannel(newChannel);
				})
				.register();
		}
	}

	public JsonObject toJson() {
		JsonObject result = new JsonObject();
		result.addProperty("type", CHANNEL_CLASS_ID);
		result.addProperty("uuid", mId.toString());
		result.addProperty("shardName", mShardName);
		result.addProperty("name", mName);
		return result;
	}

	public static String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	public UUID getUniqueId() {
		return mId;
	}

	public String getShardName() {
		return mShardName;
	}

	protected void setName(String name) throws WrapperCommandSyntaxException {
		mName = name;
	}

	public String getName() {
		return mName;
	}

	public void sendMessage(CommandSender sender, String messageText) throws WrapperCommandSyntaxException {
		// TODO Add permission check for local chat.
		Set<TransformationType> allowedTransforms = new HashSet<>();
		allowedTransforms.add(TransformationType.COLOR);
		allowedTransforms.add(TransformationType.DECORATION);
		allowedTransforms.add(TransformationType.KEYBIND);
		allowedTransforms.add(TransformationType.FONT);
		allowedTransforms.add(TransformationType.GRADIENT);
		allowedTransforms.add(TransformationType.RAINBOW);
		Message message = Message.createMessage(this, sender, null, messageText, true, allowedTransforms);

		try {
			MessageManager.getInstance().broadcastMessage(message);
		} catch (Exception e) {
			sender.sendMessage(Component.text("An exception occured broadcasting your message.", NamedTextColor.RED)
			    .hoverEvent(Component.text(e.getMessage(), NamedTextColor.RED)));
		}
	}

	public void distributeMessage(Message message) {
		// TODO Check permission to see the message.
		ChannelBase messageChannel = message.getChannel();
		try {
			if (!(messageChannel instanceof ChannelLocal) ||
				!(((ChannelLocal) messageChannel).mShardName.equals(NetworkRelayAPI.getShardName()))) {
				// TODO: Command spy goes here-ish
				return;
			}
		} catch (Exception e) {
			// Not connected to RabbitMQ - if this happens, this function doesn't get called anyways.
			return;
		}
		for (Map.Entry<UUID, PlayerState> playerStateEntry : PlayerStateManager.getPlayerStates().entrySet()) {
			UUID playerId = playerStateEntry.getKey();
			PlayerState state = playerStateEntry.getValue();

			if (state.isListening(this)) {
				state.receiveMessage(message);
			}
		}
	}

	protected void showMessage(CommandSender recipient, Message message) {
		MiniMessage minimessage = MiniMessage.builder()
			.transformation(TransformationType.COLOR)
			.transformation(TransformationType.DECORATION)
			.markdown()
			.markdownFlavor(DiscordFlavor.get())
			.build();

		// TODO Use configurable formatting, not hard-coded formatting.
		// "§7<§f" + mName + " (l)§7> §f" + message.getSender() + " §7» §f" + message.getMessage()
		String prefix = "<gray>\\<<white><channelName> (l)<gray>> <white><sender> <gray>» <white>";
		String suffix = "<hover:show_text:\"Moderator thingy?\">    </hover>";
		// TODO We should use templates to insert these and related formatting.
		prefix = prefix.replace("<channelName>", mName)
		    .replace("<sender>", message.getSenderName());

		UUID senderUuid = message.getSenderId();
		Identity senderIdentity;
		if (senderUuid == null) {
			senderIdentity = Identity.nil();
		} else {
			senderIdentity = Identity.identity(senderUuid);
		}

		Component fullMessage = Component.empty()
		    .append(minimessage.parse(prefix))
		    .append(message.getMessage())
		    .append(minimessage.parse(suffix));
		recipient.sendMessage(senderIdentity, fullMessage, MessageType.CHAT);
	}
}
