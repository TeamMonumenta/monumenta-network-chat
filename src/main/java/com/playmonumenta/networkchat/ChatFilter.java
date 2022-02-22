package com.playmonumenta.networkchat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;

import javax.annotation.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

// A collection of regex to filter chat
public class ChatFilter {
	public static class ChatFilterPattern {
		private final String mId;
		private final boolean mIsLiteral;
		private final String mPatternString;
		private final Pattern mPattern;
		private final boolean mIsBadWord;
		private String mReplacementMiniMessage;
		private @Nullable String mCommand = null;

		public ChatFilterPattern(CommandSender sender,
		                         String id,
		                         boolean isLiteral,
		                         String regex,
		                         boolean isBadWord) throws WrapperCommandSyntaxException {
			mId = id;
			mIsLiteral = isLiteral;
			mPatternString = regex;
			mIsBadWord = isBadWord;
			Pattern pattern = null;
			try {
				int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
				if (mIsLiteral) {
					flags |= Pattern.LITERAL;
				}
				pattern = Pattern.compile(mPatternString, flags);
			} catch (PatternSyntaxException e) {
				sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
				CommandAPI.fail("Could not load chat filter " + mId);
				// CommandAPI.fail always throws an exception, but the compiler isn't aware of this.
				throw new RuntimeException("Could not load chat filter " + mId);
			}
			mPattern = pattern;
			if (mIsBadWord) {
				mReplacementMiniMessage = "<red>" + mId + "</red>";
			} else {
				mReplacementMiniMessage = "<bold>$1</bold>";
			}
		}

		public static ChatFilterPattern fromJson(CommandSender sender, JsonObject object) throws Exception {
			String id = object.get("mId").getAsString();
			boolean isLiteral = object.get("mIsLiteral").getAsBoolean();
			String regex = object.get("mPatternString").getAsString();
			boolean isBadWord = object.get("mIsBadWord").getAsBoolean();
			String replacementMiniMessage = object.get("mReplacementMiniMessage").getAsString();
			@Nullable String command = null;
			if (object.has("mCommand")) {
				command = object.get("mCommand").getAsString();
			}

			ChatFilterPattern pattern = new ChatFilterPattern(sender, id, isLiteral, regex, isBadWord);
			pattern.mReplacementMiniMessage = replacementMiniMessage;
			pattern.mCommand = command;
			return pattern;
		}

		public JsonObject toJson() {
			JsonObject object = new JsonObject();
			object.addProperty("mId", mId);
			object.addProperty("mIsLiteral", mIsLiteral);
			object.addProperty("mPatternString", mPatternString);
			object.addProperty("mIsBadWord", mIsBadWord);
			object.addProperty("mReplacementMiniMessage", mReplacementMiniMessage);
			if (mCommand != null) {
				object.addProperty("mCommand", mCommand);
			}
			return object;
		}

		public String id() {
			return mId;
		}

		public String patternString() {
			return mPatternString;
		}

		public boolean isBadWord() {
			return mIsBadWord;
		}

		public String replacementMessage() {
			return mReplacementMiniMessage;
		}

		public void replacementMessage(String replacementMiniMessage) {
			mReplacementMiniMessage = replacementMiniMessage;
		}

		public @Nullable String command() {
			return mCommand;
		}

		public void command(@Nullable String command) {
			mCommand = command;
		}

		public Component run(CommandSender sender, Component component) {
			TextReplacementConfig replacementConfig = TextReplacementConfig.builder()
				.match(mPattern)
				.replacement((MatchResult match, TextComponent.Builder textBuilder) -> {
					String content = textBuilder.content();
					Component result = textBuilder.build();
					sender.sendMessage(Component.text("Found match for " + mId));
					sender.sendMessage(result);

					if (mCommand != null) {
						String command = mCommand.replace("@S", sender.getName());
						if (sender instanceof Entity) {
							command = command.replace("@U", ((Entity) sender).getUniqueId().toString().toLowerCase());
						}
						final String finishedCommand = command;
						Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(NetworkChatPlugin.getInstance(), new Runnable() {
							@Override
							public void run() {
								Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), finishedCommand);
							}
						}, 0);
					}

					return result;
				})
				.build();
			return component.replaceText(replacementConfig);
		}
	}

	private Map<String, ChatFilterPattern> mFilters = new HashMap<>();

	public static ChatFilter fromJson(CommandSender sender, JsonObject object) {
		ChatFilter filter = new ChatFilter();
		if (object != null) {
			@Nullable JsonArray filters = object.getAsJsonArray("filters");
			if (filters != null) {
				for (JsonElement patternElement : filters) {
					try {
						JsonObject patternObject = patternElement.getAsJsonObject();
						ChatFilterPattern pattern = ChatFilterPattern.fromJson(sender, patternObject);
						filter.mFilters.put(pattern.id(), pattern);
					} catch (Exception e) {
						NetworkChatPlugin.getInstance().getLogger().warning("Failed to load chat filter pattern: " + e.getMessage());
					}
				}
			}
		}
		return filter;
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		if (!mFilters.isEmpty()) {
			JsonArray filters = new JsonArray();
			for (ChatFilterPattern pattern : mFilters.values()) {
				filters.add(pattern.toJson());
			}
			object.add("filters", filters);
		}
		return object;
	}

	public void addFilter(CommandSender sender,
	                      String id,
	                      boolean isLiteral,
	                      String regex,
	                      boolean isBadWord) throws WrapperCommandSyntaxException {
		ChatFilterPattern filterPattern = new ChatFilterPattern(sender, id, isLiteral, regex, isBadWord);
		mFilters.put(id, filterPattern);
	}

	public void removeFilter(String id) {
		mFilters.remove(id);
	}

	public Map<String, ChatFilterPattern> getFilters() {
		return new HashMap<>(mFilters);
	}

	public @Nullable ChatFilterPattern getFilter(String id) {
		return mFilters.get(id);
	}

	public Component run(CommandSender sender, Component component) {
		for (ChatFilterPattern filterPattern : mFilters.values()) {
			component = filterPattern.run(sender, component);
		}
		return component;
	}
}
