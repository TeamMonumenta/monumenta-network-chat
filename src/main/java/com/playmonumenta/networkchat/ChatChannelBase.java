package com.playmonumenta.networkchat;

import org.bukkit.entity.Player;

public interface ChatChannelBase {
	// Return an identifier for this channel class.
	String getChannelClassId();

	// Return true if the player may send messages to this channel.
	boolean maySend(Player player);

	// Return true if the player may see the messages in this channel.
	boolean maySee(Player player);

	// Return true if the player may see a specific message, ie for chat within a radius.
	boolean maySee(Player player, ChatMessage message);
}
