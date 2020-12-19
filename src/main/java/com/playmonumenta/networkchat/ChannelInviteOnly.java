package com.playmonumenta.networkchat;

import java.util.List;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public interface ChannelInviteOnly {
	boolean isParticipant(CommandSender sender);

	boolean isParticipant(Player player);

	boolean isParticipant(UUID playerId);

	List<UUID> getParticipantIds();

	List<String> getParticipantNames();
}
