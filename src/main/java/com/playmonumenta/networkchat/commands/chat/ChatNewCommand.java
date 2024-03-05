package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.ChannelPredicate;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.channel.ChannelAnnouncement;
import com.playmonumenta.networkchat.channel.ChannelGlobal;
import com.playmonumenta.networkchat.channel.ChannelLocal;
import com.playmonumenta.networkchat.channel.ChannelParty;
import com.playmonumenta.networkchat.channel.ChannelTeam;
import com.playmonumenta.networkchat.channel.ChannelWhisper;
import com.playmonumenta.networkchat.channel.ChannelWorld;
import com.playmonumenta.networkchat.commands.ChatCommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import java.util.ArrayList;
import java.util.List;

public class ChatNewCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		if (NetworkChatProperties.getChatCommandCreateEnabled()) {
			arguments.add(new MultiLiteralArgument("new"));
			arguments.add(ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_LISTEN));
			ChannelAnnouncement.registerNewChannelCommands(ChatCommand.COMMANDS, new ArrayList<>(arguments));
			ChannelLocal.registerNewChannelCommands(ChatCommand.COMMANDS, new ArrayList<>(arguments));
			ChannelGlobal.registerNewChannelCommands(ChatCommand.COMMANDS, new ArrayList<>(arguments));
			ChannelParty.registerNewChannelCommands(ChatCommand.COMMANDS, new ArrayList<>(arguments));
			ChannelWorld.registerNewChannelCommands(ChatCommand.COMMANDS, new ArrayList<>(arguments));
		}
		ChannelTeam.registerNewChannelCommands();
		ChannelWhisper.registerNewChannelCommands();
	}
}
