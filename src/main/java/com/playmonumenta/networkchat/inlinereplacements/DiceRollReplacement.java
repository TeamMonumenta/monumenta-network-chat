package com.playmonumenta.networkchat.inlinereplacements;

import com.playmonumenta.networkchat.utils.MMLog;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.command.CommandSender;
import org.intellij.lang.annotations.RegExp;

public class DiceRollReplacement extends InlineReplacement {

	@RegExp
	private static final String mDiceDataGivenRegex = ".*:[1-9]?[0-9]*d[1-9][0-9]*.*";

	public DiceRollReplacement() {
		super("Dice Roll",
			"<roll(:[1-9]?[0-9]*d[1-9][0-9]*)?>",
			"roll");
	}

	@Override
	public Component replace(CommandSender sender, Component input) {
		MMLog.info("DRR: replacing string " + input);
		Component output = input;
		output = output.replaceText(matcher -> matcher.match(mRegex)
			.replacement(matchResult -> {
				MMLog.info("DRR: matched string '" + matchResult.content() + "' with regex:" + mRegex);
				String regexResult = matchResult.content();
				Pair<Integer, Integer> diceData = Pair.of(1, 100);
				if (regexResult.matches(mDiceDataGivenRegex)) {
					try {
						String[] getDiceInfo = regexResult.replaceAll("(<|>)", "")
							.split(":")[1].split("d");
						if (!getDiceInfo[0].equals("")) {
							diceData = Pair.of(Integer.parseInt(getDiceInfo[0]),
								Integer.parseInt(getDiceInfo[1]));
						} else {
							diceData = Pair.of(1, Integer.parseInt(getDiceInfo[1]));
						}
					} catch (Exception e) {
						return input;
					}
				}
				int rollValue = 0;
				for (int i = 0; i < diceData.getLeft(); i++) {
					rollValue += (int) (Math.random() * diceData.getRight()) + 1;
				}
				return Component.text(rollValue + " out of " + diceData.getLeft()
					+ "d" + diceData.getRight() + " (" + diceData.getLeft() * diceData.getRight() + ")");
			}));
		return output;
	}
}
