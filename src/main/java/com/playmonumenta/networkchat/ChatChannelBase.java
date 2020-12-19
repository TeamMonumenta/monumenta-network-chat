package com.playmonumenta.networkchat;

import org.bukkit.entity.Player;

public interface ChatChannelBase {
	// Return an identifier for this channel class.
	public String getChannelClassId();

	// Return true if the player may send messages to this channel.
	public boolean maySend(Player player);

	// Return true if the player may see the messages in this channel.
	public boolean maySee(Player player);

	// Return true if the player may see a specific message, ie for chat within a radius.
	public boolean maySee(Player player, ChatMessage message);
}
