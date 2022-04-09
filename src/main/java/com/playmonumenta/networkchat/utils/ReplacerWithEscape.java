package com.playmonumenta.networkchat.utils;

import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.text.StringEscapeUtils;
import org.bukkit.command.CommandSender;

/*
 * Matcher replacer function with escape support via "$\\#".
 * Does not support named groups.
 */
public class ReplacerWithEscape implements Function<MatchResult, String> {
	private static final Pattern RE_REPLACEMENT_ESCAPE = Pattern.compile("^\\\\(\\$|u[0-9a-fA-F]{4}|[btnfr\\\"\\']|[0-9](?![0-9])|[1-9][0-9](?![0-9])|[12][0-9]{2}|3[0-6][0-9]|37[0-7])");
	private static final Pattern RE_REPLACEMENT_LITERAL = Pattern.compile("^[^\\\\$]+");
	private static final Pattern RE_REPLACEMENT_NUMERIC_GROUP = Pattern.compile("^\\$(\\\\?)([0-9]+)");

	private final Logger mLogger;
	private final CommandSender mSender;
	private final String mReplacement;

	public ReplacerWithEscape(Logger logger, CommandSender sender, String replacement) {
		mLogger = logger;
		mSender = sender;
		mReplacement = replacement;
	}

	private static void debugMessage(Logger logger, String prefix, String msg) {
		String escaped = StringEscapeUtils.escapeJava(msg);
		logger.finer(prefix + "\"" + escaped + "\"");
	}

	@Override
	public String apply(MatchResult matchResult) {
		String remainingReplacement = mReplacement;
		StringBuilder builder = new StringBuilder();

		while (remainingReplacement.length() >= 2) {
			Matcher matcher;
			debugMessage(mLogger, "", remainingReplacement);

			matcher = RE_REPLACEMENT_LITERAL.matcher(remainingReplacement);
			if (matcher.find()) {
				String part = matcher.group();
				if (part.isBlank()) {
					debugMessage(mLogger, "    X ","Incorrect literal match, got empty!");
				} else {
					debugMessage(mLogger, "- ", part);
					builder.append(part);
					remainingReplacement = remainingReplacement.substring(part.length());
					continue;
				}
			}

			matcher = RE_REPLACEMENT_ESCAPE.matcher(remainingReplacement);
			if (matcher.find()) {
				String part = matcher.group();
				if (part.isBlank()) {
					debugMessage(mLogger, "    X ","Incorrect escape match, got empty!");
				} else {
					debugMessage(mLogger, "- ", part);
					builder.append(part);
					remainingReplacement = remainingReplacement.substring(part.length());
					continue;
				}
			}

			matcher = RE_REPLACEMENT_NUMERIC_GROUP.matcher(remainingReplacement);
			if (matcher.find()) {
				String part = matcher.group();
				String result;
				String escapeIndicator = matcher.group(1);
				boolean doEscape = !escapeIndicator.isBlank();
				String indexStr = matcher.group(2);
				debugMessage(mLogger, "- " + (doEscape ? "Escape" : "Don't Escape") + " ", indexStr);
				try {
					int groupIndex = Integer.parseInt(indexStr);
					if (0 <= groupIndex && groupIndex <= matchResult.groupCount()) {
						result = matchResult.group(groupIndex);
					} else {
						debugMessage(mLogger, "    X ","Out of bounds (" + groupIndex + " not in 0.." + matchResult.groupCount() + "), using literal instead");
						result = matcher.group();
					}
				} catch (NumberFormatException e) {
					MessagingUtils.sendStackTrace(mSender, e);
					result = matcher.group();
				}
				if (doEscape) {
					result = StringEscapeUtils.escapeJava(result);
				}
				if (result.isBlank()) {
					debugMessage(mLogger, "    X ","Incorrect numeric group match, got empty!");
				} else {
					debugMessage(mLogger, "- ", result);
					builder.append(result);
					remainingReplacement = remainingReplacement.substring(part.length());
					continue;
				}
			}

			String part = remainingReplacement.substring(0, 1);
			debugMessage(mLogger, "- ", part);
			builder.append(part);
			remainingReplacement = remainingReplacement.substring(1);
		}

		if (remainingReplacement.length() > 0) {
			debugMessage(mLogger, "", remainingReplacement);
			builder.append(remainingReplacement);
		}

		return builder.toString();
	}
}
