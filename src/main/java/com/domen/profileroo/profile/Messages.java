package com.domen.profileroo.profile;

import com.domen.profileroo.config.MessageMode;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class Messages {
	private Messages() {
	}

	/**
	 * Shows the result where the option says. In an open MaLiLib screen it goes to the screen instead, and errors are
	 * never swallowed by "none".
	 */
	public static void show(OpResult result, ConfigOptionList option) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() instanceof GuiBase) {
			InfoUtils.showGuiMessage(result.type(), result.key(), result.args());
			return;
		}
		MessageMode mode = (MessageMode) option.getOptionListValue();
		if (mode == MessageMode.NONE && !result.ok()) {
			mode = MessageMode.MESSAGE;
		}
		switch (mode) {
			case NONE -> {
			}
			case ACTIONBAR -> {
				if (mc.level != null) {
					InfoUtils.sendVanillaMessage(Component.literal(result.text()).withStyle(color(result.type())));
				} else {
					InfoUtils.showInGameMessage(result.type(), result.key(), result.args());
				}
			}
			case MESSAGE -> InfoUtils.showInGameMessage(result.type(), result.key(), result.args());
			case CHAT -> chat(result);
		}
	}

	public static void chat(OpResult result) {
		chat(Component.literal(result.text()).withStyle(color(result.type())));
	}

	public static void chat(Component text) {
		Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal("[Profileroo] ").withStyle(ChatFormatting.DARK_AQUA).append(text));
	}

	public static ChatFormatting color(Message.MessageType type) {
		return switch (type) {
			case SUCCESS -> ChatFormatting.GREEN;
			case WARNING -> ChatFormatting.GOLD;
			case ERROR -> ChatFormatting.RED;
			default -> ChatFormatting.WHITE;
		};
	}
}
