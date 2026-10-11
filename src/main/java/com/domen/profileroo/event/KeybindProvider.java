package com.domen.profileroo.event;

import com.domen.profileroo.Reference;
import com.domen.profileroo.config.Configs;
import com.domen.profileroo.gui.ConfigTab;
import com.domen.profileroo.profile.Messages;
import com.domen.profileroo.profile.OpResult;
import com.domen.profileroo.profile.ProfileManager;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.KeyAction;

public class KeybindProvider implements IKeybindProvider, IHotkeyCallback {
	public static final KeybindProvider INSTANCE = new KeybindProvider();

	private KeybindProvider() {
	}

	public void init() {
		for (ConfigHotkey hotkey : Configs.Hotkeys.HOTKEY_LIST) {
			hotkey.getKeybind().setCallback(this);
		}
	}

	@Override
	public void addKeysToMap(IKeybindManager manager) {
		for (IHotkey hotkey : Configs.Hotkeys.HOTKEY_LIST) {
			manager.addKeybindToMap(hotkey.getKeybind());
		}
		for (IHotkey hotkey : ProfileManager.getInstance().profileHotkeys()) {
			manager.addKeybindToMap(hotkey.getKeybind());
		}
	}

	@Override
	public void addHotkeys(IKeybindManager manager) {
		manager.addHotkeysForCategory(Reference.MOD_NAME, "profileroo.hotkeys.category.generic", Configs.Hotkeys.HOTKEY_LIST);
		manager.addHotkeysForCategory(Reference.MOD_NAME, "profileroo.hotkeys.category.profiles", ProfileManager.getInstance().profileHotkeys());
	}

	@Override
	public boolean onKeyAction(KeyAction action, IKeybind key) {
		ProfileManager manager = ProfileManager.getInstance();
		OpResult result;
		if (key == Configs.Hotkeys.OPEN_GUI.getKeybind()) {
			ConfigTab.openLast(null);
			return true;
		} else if (key == Configs.Hotkeys.NEXT_PROFILE.getKeybind()) {
			result = manager.cycle(1);
		} else if (key == Configs.Hotkeys.PREVIOUS_PROFILE.getKeybind()) {
			result = manager.cycle(-1);
		} else if (key == Configs.Hotkeys.SAVE_PROFILE.getKeybind()) {
			result = manager.saveActive();
		} else if (key == Configs.Hotkeys.RESET_PROFILE.getKeybind()) {
			result = manager.resetActive();
		} else if (key == Configs.Hotkeys.TOGGLE_AUTO_SWITCH.getKeybind()) {
			result = manager.setAutoSwitch(!manager.isAutoSwitch());
		} else {
			return false;
		}
		Messages.show(result, Configs.Generic.MESSAGE_ON_SWITCH);
		return true;
	}
}
