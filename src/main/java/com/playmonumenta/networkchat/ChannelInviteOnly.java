package com.playmonumenta.networkchat;

import java.util.List;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public interface ChannelInviteOnly {
	public boolean isParticipant(CommandSender sender);

	public boolean isParticipant(Player player);

	public boolean isParticipant(UUID playerId);

	public List<UUID> getParticipantIds();

	public List<String> getParticipantNames();
}
