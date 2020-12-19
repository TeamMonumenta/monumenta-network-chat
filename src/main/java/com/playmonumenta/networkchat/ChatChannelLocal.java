package com.playmonumenta.networkchat;

import org.bukkit.entity.Player;

public class ChatChannelLocal implements ChatChannelBase {
	public static final String CHANNEL_CLASS_ID = "Local";

	public String getChannelClassId() {
		return CHANNEL_CLASS_ID;
	}

	public boolean maySend(Player player) {
		return true;
	}

	public boolean maySee(Player player) {
		return true;
	}

	public boolean maySee(Player player, ChatMessage message) {
		return true;
	}
}
