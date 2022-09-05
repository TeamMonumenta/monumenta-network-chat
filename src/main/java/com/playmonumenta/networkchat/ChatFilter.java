package com.playmonumenta.networkchat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.networkchat.utils.ReplacerWithEscape;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.text.StringEscapeUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

// A collection of regex to filter chat
public class ChatFilter {
	public static class ChatFilterResult {
		private boolean mFoundMatch = false;
		private boolean mFoundBadWord = false;
		private Component mOriginalComponent;
		private Component mComponent;

		public ChatFilterResult(Component component) {
			mOriginalComponent = component;
			mComponent = component;
		}

		public ChatFilterResult getCleanCopy() {
			ChatFilterResult filterResult = new ChatFilterResult(mComponent);
			filterResult.mOriginalComponent = mOriginalComponent;
			return filterResult;
		}

		public void copyResults(ChatFilterResult other) {
			mFoundMatch |= other.mFoundMatch;
			mFoundBadWord |= other.mFoundBadWord;
			mComponent = other.mComponent;
		}

		public boolean foundMatch() {
			return mFoundMatch;
		}

		public void foundMatch(boolean value) {
			mFoundMatch = value;
		}

		public boolean foundBadWord() {
			return mFoundBadWord;
		}

		public void foundBadWord(boolean value) {
			mFoundBadWord = value;
		}

		public Component originalComponent() {
			return mOriginalComponent;
		}

		public Component component() {
			return mComponent;
		}

		public void component(Component component) {
			mComponent = component;
		}
	}

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
			Pattern pattern;
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
				mReplacementMiniMessage = "<bold>$0</bold>";
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

		public ChatFilterPattern replacementMessage(String replacementMiniMessage) {
			mReplacementMiniMessage = replacementMiniMessage;
			return this;
		}

		public @Nullable String command() {
			return mCommand;
		}

		public ChatFilterPattern command(@Nullable String command) {
			mCommand = command;
			return this;
		}

		public void run(CommandSender sender, final ChatFilterResult filterResult) {
			Logger logger = NetworkChatPlugin.getInstance().getLogger();
			CommandSender callee = CommandUtils.getCallee(sender);
			ReplacerWithEscape replacer = new ReplacerWithEscape(logger, sender, mReplacementMiniMessage);
			final ChatFilterResult localResult = filterResult.getCleanCopy();
			TextReplacementConfig replacementConfig = TextReplacementConfig.builder()
				.match(mPattern)
				.replacement((MatchResult match, TextComponent.Builder textBuilder) -> {
					localResult.foundMatch(true);
					if (mIsBadWord) {
						localResult.foundBadWord(true);
					}
					String content = textBuilder.content();
					String finalContent = content;
					NetworkChatPlugin.getInstance().getLogger().finer(() -> "    <- " + finalContent);
					content = mPattern.matcher(content).replaceAll(replacer);
					String finalContent1 = content;
					NetworkChatPlugin.getInstance().getLogger().finer(() -> "    -- " + finalContent1);
					Component replacementResult = MessagingUtils.SENDER_FMT_MINIMESSAGE.deserialize(content);
					NetworkChatPlugin.getInstance().getLogger().finer(() -> "    -> " + MessagingUtils.SENDER_FMT_MINIMESSAGE.serialize(replacementResult));
					return replacementResult;
				})
				.build();

			NetworkChatPlugin.getInstance().getLogger().finer(() -> "  ..." + MessagingUtils.SENDER_FMT_MINIMESSAGE.serialize(localResult.component()));
			localResult.component(localResult.component().replaceText(replacementConfig));
			NetworkChatPlugin.getInstance().getLogger().finer(() -> "  ..." + MessagingUtils.SENDER_FMT_MINIMESSAGE.serialize(localResult.component()));

			String plainText = MessagingUtils.plainText(localResult.component());
			String plainReplacement = mPattern.matcher(plainText).replaceAll(replacer);
			if (!plainText.equals(plainReplacement)) {
				localResult.foundMatch(true);
				if (mIsBadWord) {
					localResult.foundBadWord(true);
				}
				localResult.component(MessagingUtils.SENDER_FMT_MINIMESSAGE.deserialize(plainReplacement));
			}

			if (localResult.foundMatch()) {
				if (mCommand != null) {
					String command = mCommand.replace("@S", sender.getName());
					if (callee instanceof Entity entity) {
						command = command.replace("@U", entity.getUniqueId().toString().toLowerCase());
					}
					String originalMessage = MessagingUtils.plainText(localResult.originalComponent());
					String replacedMessage = MessagingUtils.plainText(localResult.component());
					command = command.replace("@OE", StringEscapeUtils.escapeJson(originalMessage));
					command = command.replace("@ME", StringEscapeUtils.escapeJson(replacedMessage));
					command = command.replace("@O", originalMessage);
					command = command.replace("@M", replacedMessage);
					final String finishedCommand = command;
					Bukkit.getScheduler().runTask(NetworkChatPlugin.getInstance(),
						() -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), finishedCommand));
				}
			}
			filterResult.copyResults(localResult);

			NetworkChatPlugin.getInstance().getLogger().finer(() -> "- " + mId + ":");
			NetworkChatPlugin.getInstance().getLogger().finer(() -> MessagingUtils.SENDER_FMT_MINIMESSAGE.serialize(filterResult.component()));
		}
	}

	private final Map<String, ChatFilterPattern> mFilters = new HashMap<>();

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

	public ChatFilterPattern addFilter(CommandSender sender,
	                      String id,
	                      boolean isLiteral,
	                      String regex,
	                      boolean isBadWord) throws WrapperCommandSyntaxException {
		ChatFilterPattern filterPattern = new ChatFilterPattern(sender, id, isLiteral, regex, isBadWord);
		mFilters.put(id, filterPattern);
		return filterPattern;
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

	public void run(CommandSender sender, ChatFilterResult filterResult) {
		NetworkChatPlugin.getInstance().getLogger().finer("Start:");
		NetworkChatPlugin.getInstance().getLogger().finer(() -> MessagingUtils.SENDER_FMT_MINIMESSAGE.serialize(filterResult.component()));
		for (ChatFilterPattern filterPattern : mFilters.values()) {
			filterPattern.run(sender, filterResult);
		}
	}

	public Component run(CommandSender sender, Component component) {
		ChatFilterResult filterResult = new ChatFilterResult(component);
		run(sender, filterResult);
		return filterResult.component();
	}

	public String run(CommandSender sender, String plainText) {
		Component component = Component.text(plainText);
		component = run(sender, component);
		return MessagingUtils.plainText(component);
	}

	public boolean hasBadWord(CommandSender sender, Component component) {
		ChatFilterResult filterResult = new ChatFilterResult(component);
		run(sender, filterResult);
		return filterResult.foundBadWord();
	}
}
