package com.domen.profileroo.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

/** Where to tell the player that the profile changed. */
public enum MessageMode implements IConfigOptionListEntry {
	NONE("none"),
	ACTIONBAR("actionbar"),
	MESSAGE("message"),
	CHAT("chat");

	private final String configString;

	MessageMode(String configString) {
		this.configString = configString;
	}

	@Override
	public String getStringValue() {
		return this.configString;
	}

	@Override
	public String getDisplayName() {
		return StringUtils.translate("profileroo.label.message_mode." + this.configString);
	}

	@Override
	public IConfigOptionListEntry cycle(boolean forward) {
		MessageMode[] values = values();
		int id = (this.ordinal() + (forward ? 1 : values.length - 1)) % values.length;
		return values[id];
	}

	@Override
	public MessageMode fromString(String name) {
		for (MessageMode mode : values()) {
			if (mode.configString.equalsIgnoreCase(name)) {
				return mode;
			}
		}
		return NONE;
	}
}
