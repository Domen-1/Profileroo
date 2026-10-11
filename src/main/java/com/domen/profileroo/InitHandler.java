package com.domen.profileroo;

import com.domen.profileroo.command.ProfileCommand;
import com.domen.profileroo.config.Configs;
import com.domen.profileroo.event.KeybindProvider;
import com.domen.profileroo.event.WorldLoadListener;
import com.domen.profileroo.gui.GuiConfigs;
import com.domen.profileroo.profile.ProfileManager;
import fi.dy.masa.malilib.command.ClientCommandHandler;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.event.WorldLoadHandler;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;

public class InitHandler implements IInitializationHandler {
	@Override
	public void registerModHandlers() {
		ConfigManager.getInstance().registerConfigHandler(Reference.MOD_ID, new Configs());
		Registry.CONFIG_SCREEN.registerConfigScreenFactory(new ModInfo(Reference.MOD_ID, Reference.MOD_NAME, GuiConfigs::new));

		// state.json is ours, not a MaLiLib config: read it now, so the profile hotkeys exist when MaLiLib maps keys.
		ProfileManager.getInstance().loadState();

		KeybindProvider.INSTANCE.init();
		InputEventHandler.getKeybindManager().registerKeybindProvider(KeybindProvider.INSTANCE);

		WorldLoadListener worldListener = new WorldLoadListener();
		WorldLoadHandler.getInstance().registerWorldLoadPreHandler(worldListener);
		WorldLoadHandler.getInstance().registerWorldLoadPostHandler(worldListener);

		ClientCommandHandler.INSTANCE.registerCommand(new ProfileCommand());
	}
}
