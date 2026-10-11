package com.domen.profileroo.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/** The tab row shared by all of the mod's screens: three config lists and three list editors. */
public enum ConfigTab {
	PROFILES("profiles"),
	SERVERS("servers"),
	MODS("mods"),
	GENERIC("generic"),
	HOTKEYS("hotkeys"),
	PROFILE_HOTKEYS("profile_hotkeys");

	private static ConfigTab last = PROFILES;

	private final String translationKey;

	ConfigTab(String name) {
		this.translationKey = "profileroo.gui.button.tab." + name;
	}

	public String displayName() {
		return StringUtils.translate(this.translationKey);
	}

	public boolean isConfigList() {
		return this == GENERIC || this == HOTKEYS || this == PROFILE_HOTKEYS;
	}

	public GuiBase create(@Nullable Screen parent) {
		GuiBase gui = switch (this) {
			case PROFILES -> new GuiProfiles();
			case SERVERS -> new GuiServers();
			case MODS -> new GuiMods();
			case GENERIC, HOTKEYS, PROFILE_HOTKEYS -> new GuiConfigs(this);
		};
		gui.setParent(parent);
		return gui;
	}

	public void open(@Nullable Screen parent) {
		last = this;
		GuiBase.openGui(this.create(parent));
	}

	public static void openLast(@Nullable Screen parent) {
		last.open(parent);
	}

	/** Adds the tab buttons at {@code x, y}; the current tab's button is disabled. */
	public static void addButtons(GuiBase gui, int x, int y, ConfigTab current) {
		for (ConfigTab tab : values()) {
			ButtonGeneric button = new ButtonGeneric(x, y, -1, 20, tab.displayName());
			button.setEnabled(tab != current);
			gui.addButton(button, (b, mouseButton) -> tab.open(gui.getParent()));
			x += button.getWidth() + 2;
		}
	}
}
