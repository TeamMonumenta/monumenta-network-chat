package com.playmonumenta.networkchat;

import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;

// A channel that has not finished loading. This may not be saved.
public class ChannelLoading extends Channel {
	public static final String CHANNEL_CLASS_ID = "loading";

	private final UUID mId;
	private final Set<PlayerState> mAwaitingPlayers = new HashSet<>();
	private final List<Message> mQueuedMessages = new ArrayList<>();

	/* Note: This channel type may only be created by this plugin while waiting for Redis data.
	 * This is why it is the only channel whose constructor is protected, and has no name. */
	protected ChannelLoading(UUID channelId) {
		mId = channelId;
	}

	// NOTE: This channel type should never be saved, as it could overwrite a real channel.
	// toJson() shouldn't even be called for this class.
	@Override
	public JsonObject toJson() {
		return null;
	}

	// NOTE: If this class ID is ever read, it means the channel hasn't loaded and should be ignored.
	@Override
	public String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	@Override
	public UUID getUniqueId() {
		return mId;
	}

	@Override
	protected void setName(String name) throws WrapperCommandSyntaxException {
		CommandAPI.fail("This channel is still loading, please try again.");
	}

	// NOTE: This should not be called for this class.
	@Override
	public String getName() {
		return null;
	}

	@Override
	public @Nullable TextColor color() {
		return null;
	}

	@Override
	public void color(CommandSender sender, @Nullable TextColor color) throws WrapperCommandSyntaxException {
		CommandUtils.fail(sender,"This channel is still loading, please try again.");
	}

	@Override
	public void sendMessage(CommandSender sender, String message) throws WrapperCommandSyntaxException {
		CommandUtils.fail(sender, "This channel is still loading, please try again.");
	}

	// Messages will be replayed for anyone triggering the channel to load, nothing to do.
	@Override
	public void distributeMessage(Message message) {}

	// The channel is loading - we don't know how to display this message yet!
	@Override
	protected Component shownMessage(CommandSender recipient, Message message) {
		return message.getMessage();
	}

	// The channel is loading - we can't determine who can see this yet!
	@Override
	protected void showMessage(CommandSender recipient, Message message) {}

	// Register a player state waiting for this channel to finish loading.
	public void addWaitingPlayerState(PlayerState playerState) {
		mAwaitingPlayers.add(playerState);
	}

	// Register a message waiting for this channel to finish loading.
	public void addMessage(Message message) {
		mQueuedMessages.add(message);
	}

	// Notify the player the channel has finish loading, or failed to load.
	protected void finishLoading() {
		Channel channel = ChannelManager.getChannel(mId);

		// Update awaiting player states regardless of load success (will show deletion on failure)
		for (PlayerState playerState : mAwaitingPlayers) {
			playerState.channelUpdated(mId, channel);
		}

		// If the newly loaded channel is valid, distribute messages that were missed
		if (channel != null && !(channel instanceof ChannelFuture)) {
			for (Message message : mQueuedMessages) {
				channel.distributeMessage(message);
			}
		}
	}
}
