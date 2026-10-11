package com.domen.profileroo.profile;

import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.StringUtils;

/** Outcome of a profile operation: a message for the player, already sorted into success, info, warning or error. */
public record OpResult(Message.MessageType type, String key, Object... args) {
	public static OpResult success(String key, Object... args) {
		return new OpResult(Message.MessageType.SUCCESS, key, args);
	}

	public static OpResult info(String key, Object... args) {
		return new OpResult(Message.MessageType.INFO, key, args);
	}

	public static OpResult warning(String key, Object... args) {
		return new OpResult(Message.MessageType.WARNING, key, args);
	}

	public static OpResult error(String key, Object... args) {
		return new OpResult(Message.MessageType.ERROR, key, args);
	}

	public boolean ok() {
		return this.type != Message.MessageType.ERROR;
	}

	public boolean changed() {
		return this.type == Message.MessageType.SUCCESS;
	}

	public String text() {
		return StringUtils.translate(this.key, this.args);
	}
}
