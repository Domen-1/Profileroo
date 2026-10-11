package com.domen.profileroo.compat;

import com.domen.profileroo.gui.ConfigTab;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuImpl implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigTab.PROFILES::create;
	}
}
