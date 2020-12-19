package com.playmonumenta.networkchat.util;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.KeybindComponent;
import net.md_5.bungee.api.chat.ScoreComponent;
import net.md_5.bungee.api.chat.SelectorComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.api.chat.hover.content.Content;
import net.md_5.bungee.api.chat.hover.content.Entity;
import net.md_5.bungee.api.chat.hover.content.Item;
import net.md_5.bungee.api.chat.hover.content.Text;

public interface ChatComponentUtils {
	public static JsonElement toJson(BaseComponent component) {
		if (component == null) {
			return null;
		}

		JsonObject rawJsonText = new JsonObject();

		// Handle formatting
		Boolean flag;
		flag = component.isBoldRaw();
		if (flag != null) {
			rawJsonText.addProperty("bold", flag);
		}
		flag = component.isItalicRaw();
		if (flag != null) {
			rawJsonText.addProperty("italic", flag);
		}
		flag = component.isUnderlinedRaw();
		if (flag != null) {
			rawJsonText.addProperty("underlined", flag);
		}
		flag = component.isStrikethroughRaw();
		if (flag != null) {
			rawJsonText.addProperty("strikethrough", flag);
		}
		flag = component.isObfuscatedRaw();
		if (flag != null) {
			rawJsonText.addProperty("obfuscated", flag);
		}
		ChatColor color = component.getColorRaw();
		if (flag != null) {
			rawJsonText.addProperty("color", color.toString().toLowerCase());
		}

		if (component instanceof KeybindComponent) {
			KeybindComponent keybindComponent = (KeybindComponent) component;
			rawJsonText.addProperty("keybind", keybindComponent.getKeybind());
		} else if (component instanceof ScoreComponent) {
			ScoreComponent scoreComponent = (ScoreComponent) component;
			JsonObject scoreJson = new JsonObject();
			scoreJson.addProperty("name", scoreComponent.getName());
			scoreJson.addProperty("objective", scoreComponent.getObjective());
			String scoreValue = scoreComponent.getValue();
			if (scoreValue != null) {
				scoreJson.addProperty("objective", scoreValue);
			}
			rawJsonText.add("score", scoreJson);
		} else if (component instanceof SelectorComponent) {
			SelectorComponent selectorComponent = (SelectorComponent) component;
			rawJsonText.addProperty("selector", selectorComponent.getSelector());
		} else if (component instanceof TextComponent) {
			TextComponent textComponent = (TextComponent) component;
			rawJsonText.addProperty("text", textComponent.getText());
		} else if (component instanceof TranslatableComponent) {
			TranslatableComponent translatableComponent = (TranslatableComponent) component;
			rawJsonText.addProperty("translate", translatableComponent.getTranslate());
			List<BaseComponent> with = translatableComponent.getWith();
			if (with != null && !with.isEmpty()) {
				JsonArray withJson = new JsonArray();
				for (BaseComponent withComponent : with) {
					withJson.add(toJson(withComponent));
				}
				rawJsonText.add("with", withJson);
			}
		}

		String insertion = component.getInsertion();
		if (insertion != null && !insertion.isEmpty()) {
			rawJsonText.addProperty("insertion", insertion);
		}

		ClickEvent clickEvent = component.getClickEvent();
		if (clickEvent != null) {
			JsonObject clickJson = new JsonObject();
			clickJson.add("action", clickEvent.getAction());
			clickJson.add("value", clickEvent.getValue());
			rawJsonText.add("clickEvent", clickJson);
		}
		
		HoverEvent hoverEvent = component.getHoverEvent();
		if (hoverEvent != null) {
			JsonObject hoverJson = new JsonObject();
			HoverEvent.Action action = hoverEvent.getAction();
			hoverJson.add("action", action.toString().toLowerCase());

			List<Content> contents = hoverEvent.getContents();
			JsonElement hoverActionJson;
			switch (action) {
			case HoverEvent.Action.SHOW_ENTITY:
				// TODO
				hoverJson.add("contents", hoverActionJson);
				break;
			case HoverEvent.Action.SHOW_ITEM:
				// TODO
				hoverJson.add("contents", hoverActionJson);
				break;
			case HoverEvent.Action.SHOW_TEXT:
				// TODO
				hoverJson.add("contents", hoverActionJson);
				break;
			}
			contents

			rawJsonText.add("hoverEvent", hoverJson);
		}

		List<BaseComponent> extra = component.getExtra();
		if (extra != null && !extra.isEmpty()) {
			JsonArray extraJson = new JsonArray();
			for (BaseComponent extraComponent : extra) {
				extraJson.add(toJson(extraComponent));
			}
			rawJsonText.add("extra", extraJson);
		}

		return rawJsonText;
	}
}
