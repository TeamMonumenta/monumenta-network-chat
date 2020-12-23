package com.playmonumenta.networkchat.util;

import java.util.Arrays;
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
import net.md_5.bungee.api.chat.ItemTag;
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
	public static BaseComponent[] fromJson(JsonElement rawJsonText) throws Exception {
		if (rawJsonText == null) {
			throw new Exception("RawJsonText is null.");
		}

		if (rawJsonText.isJsonPrimitive()) {
			BaseComponent[] primitiveResult = new BaseComponent[1];
			primitiveResult[0] = new TextComponent(rawJsonText.getAsJsonPrimitive().getAsString());
			return primitiveResult;

		} else if (rawJsonText.isJsonArray()) {
			JsonArray rawJsonComponents = rawJsonText.getAsJsonArray();
			List<BaseComponent> components = new ArrayList<BaseComponent>();
			for (JsonElement componentJson : rawJsonComponents) {
				BaseComponent[] parsedComponentParts = fromJson(componentJson);
				if (parsedComponentParts.length == 0) {
					throw new Exception("RawJsonText array has length of 0.");
				}

				BaseComponent component;
				if (parsedComponentParts.length == 1) {
					component = parsedComponentParts[0];
				} else {
					component = new TextComponent("");
					component.setExtra(Arrays.asList(parsedComponentParts));
				}
				components.add(component);
			}
			return components.toArray(new BaseComponent[0]);

		} else if (rawJsonText.isJsonObject()) {
			JsonObject rawJsonTextObj = rawJsonText.getAsJsonObject();
			BaseComponent rootComponent;

			// Handle the different component types.
			if (rawJsonTextObj.has("text")) {
				String textValue = rawJsonTextObj.getAsJsonPrimitive("text").getAsString();
				rootComponent = new TextComponent(textValue);

			} else if (rawJsonTextObj.has("translate")) {
				String translateValue = rawJsonTextObj.getAsJsonPrimitive("translate").getAsString();

				List<BaseComponent> withValuesList = new ArrayList<BaseComponent>();
				JsonArray withJsonArray = rawJsonTextObj.getAsJsonArray("with");
				if (withJsonArray != null) {
					for (JsonElement withSlotJson : withJsonArray) {
						BaseComponent[] withSlotParts = fromJson(withSlotJson);
						TextComponent withSlot = new TextComponent("");
						withSlot.setExtra(Arrays.asList(withSlotParts));
						withValuesList.add(withSlot);
					}
				}
				BaseComponent[] withValues = withValuesList.toArray(new BaseComponent[0]);

				rootComponent = new TranslatableComponent(translateValue, withValues);

			} else if (rawJsonTextObj.has("score")) {
				JsonObject scoreJson = rawJsonTextObj.getAsJsonObject("score");
				String scoreName = scoreJson.getAsJsonPrimitive("name").getAsString();
				String scoreObjective = scoreJson.getAsJsonPrimitive("objective").getAsString();
				String scoreValue = scoreJson.getAsJsonPrimitive("value").getAsString();

				if (scoreValue == null) {
					rootComponent = new ScoreComponent(scoreName, scoreObjective);
				} else {
					rootComponent = new ScoreComponent(scoreName, scoreObjective, scoreValue);
				}

			} else if (rawJsonTextObj.has("selector")) {
				String selectorValue = rawJsonTextObj.getAsJsonPrimitive("selector").getAsString();
				rootComponent = new SelectorComponent(selectorValue);

			} else if (rawJsonTextObj.has("keybind")) {
				String keybindValue = rawJsonTextObj.getAsJsonPrimitive("keybind").getAsString();
				rootComponent = new KeybindComponent(keybindValue);

			} else if (rawJsonTextObj.has("nbt")) {
				throw new Exception("NbtComponent was not available at the time this plugin was created, sorry!");

			} else {
				throw new Exception("Unknown RawJsonText component type.");
			}

			if (rawJsonTextObj.has("bold")) {
				rootComponent.setBold(rawJsonTextObj.getAsJsonPrimitive("bold").getAsBoolean());
			}
			if (rawJsonTextObj.has("italic")) {
				rootComponent.setItalic(rawJsonTextObj.getAsJsonPrimitive("italic").getAsBoolean());
			}
			if (rawJsonTextObj.has("underlined")) {
				rootComponent.setUnderlined(rawJsonTextObj.getAsJsonPrimitive("underlined").getAsBoolean());
			}
			if (rawJsonTextObj.has("strikethrough")) {
				rootComponent.setStrikethrough(rawJsonTextObj.getAsJsonPrimitive("strikethrough").getAsBoolean());
			}
			if (rawJsonTextObj.has("obfuscated")) {
				rootComponent.setStrikethrough(rawJsonTextObj.getAsJsonPrimitive("obfuscated").getAsBoolean());
			}

			if (rawJsonTextObj.has("color")) {
				String colorString = rawJsonTextObj.getAsJsonPrimitive("color").getAsString();
				// If errors occur on ChatColor.of("#RRGGBB"), more advanced parsing is required.
				rootComponent.setColor(ChatColor.of(colorString));
			}

			if (rawJsonTextObj.has("insertion")) {
				String insertionString = rawJsonTextObj.getAsJsonPrimitive("insertion").getAsString();
				rootComponent.setInsertion(insertionString);
			}

			if (rawJsonTextObj.has("clickEvent")) {
				JsonObject clickEventJson = rawJsonTextObj.getAsJsonObject("clickEvent");
				ClickEvent.Action clickAction = ClickEvent.Action.valueOf(clickEventJson.getAsJsonPrimitive("action").getAsString().toUpperCase());
				String clickValue = clickEventJson.getAsJsonPrimitive("value").getAsString();
				ClickEvent clickEvent = new ClickEvent(clickAction, clickValue);
				rootComponent.setClickEvent(clickEvent);
			}

			if (rawJsonTextObj.has("hoverEvent")) {
				JsonObject hoverEventJson = rawJsonTextObj.getAsJsonObject("hoverEvent");
				HoverEvent.Action hoverAction = HoverEvent.Action.valueOf(hoverEventJson.getAsJsonPrimitive("action").getAsString().toUpperCase());
				List<Content> contents = new ArrayList<Content>();
				if (hoverAction.equals(HoverEvent.Action.SHOW_ENTITY)) {
					JsonObject shownEntityJson = hoverEventJson.getAsJsonObject("contents");
					if (shownEntityJson == null) {
						shownEntityJson = hoverEventJson.getAsJsonObject("value");
					}

					String shownEntityType = null;
					String shownEntityId = "00000000-0000-0000-0000-000000000000";
					BaseComponent shownEntityName = null;

					JsonPrimitive shownEntityTypeJson = shownEntityJson.getAsJsonPrimitive("type");
					if (shownEntityTypeJson != null) {
						shownEntityType = shownEntityTypeJson.getAsString();
					}

					JsonElement shownEntityNameJson = shownEntityJson.get("name");
					if (shownEntityNameJson != null) {
						BaseComponent[] shownEntityNameComponents = fromJson(shownEntityNameJson);
						if (shownEntityNameComponents.length == 1) {
							shownEntityName = shownEntityNameComponents[0];
						} else {
							shownEntityName = new TextComponent("");
							shownEntityName.setExtra(Arrays.asList(shownEntityNameComponents));
						}
					}

					contents.add(new Entity(shownEntityType, shownEntityId, shownEntityName));

				} else if (hoverAction.equals(HoverEvent.Action.SHOW_ITEM)) {
					JsonObject shownItemContents = hoverEventJson.getAsJsonObject("contents");

					String shownItemId = "minecraft:air";
					int shownItemCount = 1;
					String shownItemTagString = null;

					if (shownItemContents != null) {
						shownItemId = shownItemContents.getAsJsonPrimitive("id").getAsString();
						JsonPrimitive shownItemCountJson = shownItemContents.getAsJsonPrimitive("count");
						if (shownItemCountJson != null) {
							shownItemCount = shownItemCountJson.getAsInt();
						}
						JsonPrimitive shownItemTagJson = shownItemContents.getAsJsonPrimitive("tag");
						if (shownItemTagJson != null) {
							shownItemTagString = shownItemTagJson.getAsString();
						}
					}/* else {
						JsonPrimitive shownItemLegacyValue = hoverEventJson.getAsJsonPrimitive("value");
						String shownItemSlotMojangson = shownItemLegacyValue.getAsString();
						// TODO HERE
					}*/

					ItemTag shownItemTag = null;
					if (shownItemTagString != null) {
						shownItemTag = ItemTag.ofNbt(shownItemTagString);
					}
					contents.add(new Item(shownItemId, shownItemCount, shownItemTag));

				} else if (hoverAction.equals(HoverEvent.Action.SHOW_TEXT)) {
					JsonElement shownTextJson = hoverEventJson.get("contents");
					if (shownTextJson == null) {
						shownTextJson = hoverEventJson.get("value");
					}
					contents.add(new Text(fromJson(shownTextJson)));

				} else {
					throw new Exception("Unknown hoverEvent action" + hoverEventJson.getAsJsonPrimitive("action").getAsString() + ".");
				}

				HoverEvent hoverEvent = new HoverEvent(hoverAction, contents);
				rootComponent.setHoverEvent(hoverEvent);
			}

			if (rawJsonTextObj.has("font")) {
				rootComponent.setFont(rawJsonTextObj.getAsJsonPrimitive("font").getAsString());
			}

			if (rawJsonTextObj.has("extra")) {
				rootComponent.setExtra(Arrays.asList(fromJson(rawJsonTextObj.get("extra"))));
			}

			BaseComponent[] components = new BaseComponent[1];
			components[0] = rootComponent;
			return components;

		} else {
			throw new Exception("Unsupported json element for RawJsonText.");
		}
	}

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
		if (color != null) {
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
		} else {
			// TODO Error if not instanceof these from an updated version with the other raw json text types?
			return null;
		}

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

				if (item.getTag() != null && item.getTag().getNbt() != null && !item.getTag().getNbt().equals("{}")) {
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

		String font = component.getFontRaw();
		if (font != null && !font.isEmpty()) {
			rawJsonText.addProperty("font", font);
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
