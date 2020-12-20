package com.playmonumenta.networkchat.util;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

public class ChatComponentUtils {
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
		} // TODO Error if not instanceof these from an updated version with the other raw json text types?

		String insertion = component.getInsertion();
		if (insertion != null && !insertion.isEmpty()) {
			rawJsonText.addProperty("insertion", insertion);
		}

		ClickEvent clickEvent = component.getClickEvent();
		if (clickEvent != null) {
			JsonObject clickJson = new JsonObject();
			clickJson.addProperty("action", clickEvent.getAction().toString().toLowerCase());
			clickJson.addProperty("value", clickEvent.getValue());
			rawJsonText.add("clickEvent", clickJson);
		}
		
		HoverEvent hoverEvent = component.getHoverEvent();
		if (hoverEvent != null) {
			JsonObject hoverJson = new JsonObject();
			HoverEvent.Action action = hoverEvent.getAction();
			hoverJson.addProperty("action", action.toString().toLowerCase());

			List<Content> contents = hoverEvent.getContents();
			switch (action) {
			case SHOW_ENTITY:
				JsonObject showEntityObj = new JsonObject();
				Entity entity = (Entity) contents.get(0);

				String entityId = entity.getId();
				if (entityId != null && !entityId.isEmpty()) {
					showEntityObj.addProperty("id", entityId);
				}

				String entityType = entity.getType();
				if (entityType != null && !entityType.isEmpty()) {
					showEntityObj.addProperty("type", entityType);
				}

				BaseComponent entityName = entity.getName();
				JsonElement entityNameJson = toJson(entityName);
				if (entityNameJson != null) {
					showEntityObj.add("name", entityNameJson);
				}

				hoverJson.add("contents", showEntityObj);
				break;
			case SHOW_ITEM:
				Item item = (Item) contents.get(0);
				JsonObject itemJson = new JsonObject();

				itemJson.addProperty("id", item.getId());

				int itemCount = item.getCount();
				if (itemCount > 1) {
					itemJson.addProperty("count", itemCount);
				}

				if (item.getTag() != null && item.getTag().getNbt() != null) {
					itemJson.addProperty("tag", item.getTag().getNbt());
				}

				hoverJson.add("contents", itemJson);
				break;
			case SHOW_TEXT:
				JsonArray hoverTextJson = new JsonArray();

				for (Content content : contents) {
					Text hoverTextPart = (Text) content;
					if (hoverTextPart == null) {
						// TODO Error?
						continue;
					}

					if (hoverTextPart.getValue() instanceof BaseComponent) {
						JsonElement hoverTextPartJson = toJson((BaseComponent) (hoverTextPart.getValue()));
						hoverTextJson.add(hoverTextPartJson);
					} else if (hoverTextPart.getValue() instanceof String) {
						hoverTextJson.add((String) (hoverTextPart.getValue()));
					} else {
						// TODO Error?
						continue;
					}
				}

				hoverJson.add("contents", hoverTextJson);
				break;
			default:
				// TODO Default error/not implemented?
				break;
			}

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
