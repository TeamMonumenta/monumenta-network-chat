package com.playmonumenta.networkchat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;

import org.bukkit.command.CommandSender;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

// A channel that has not finished loading. This may not be saved.
public class ChannelLoading extends ChannelBase {
	public static final String CHANNEL_CLASS_ID = "loading";

	private UUID mId;
	private Set<PlayerState> mAwaitingPlayers = new HashSet<>();

	/* Note: This channel type may only be created by this plugin while waiting for Redis data.
	 * This is why it is the only channel whose constructor is protected, and has no name. */
	protected ChannelLoading(UUID channelId) {
		mId = channelId;
	}

	// NOTE: This channel type should never be saved, as it could overwrite a real channel.
	// toJson() shouldn't even be called for this class.
	public JsonObject toJson() {
		return null;
	}

	// NOTE: If this class ID is ever read, it means the channel hasn't loaded and should be ignored.
	public static String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	public UUID getUniqueId() {
		return mId;
	}

	protected void setName(String name) throws WrapperCommandSyntaxException {
		CommandAPI.fail("This channel is still loading, please try again.");
	}

	// NOTE: This should not be called for this class.
	public String getName() {
		return null;
	}

	public void sendMessage(CommandSender sender, String message) throws WrapperCommandSyntaxException {
		CommandAPI.fail("This channel is still loading, please try again.");
	}

	// Messages will be replayed for anyone triggering the channel to load, nothing to do.
	public void distributeMessage(Message message) {
		;
	}

	// The channel is loading - we can't determine who can see this yet!
	protected void showMessage(CommandSender recipient, Message message) {
		;
	}

	// Register a player state waiting for this channel to finish loading.
	public void addWaitingPlayerState(PlayerState playerState) {
		mAwaitingPlayers.add(playerState);
	}

	// Notify the player the channel has finish loading, or failed to load.
	protected void alertPlayerStates() {
		for (PlayerState playerState : mAwaitingPlayers) {
			playerState.channelLoaded(mId);
		}
	}
}
