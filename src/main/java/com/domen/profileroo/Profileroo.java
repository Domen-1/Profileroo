package com.domen.profileroo;

import com.domen.profileroo.config.Configs;
import fi.dy.masa.malilib.event.InitializationHandler;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Profileroo implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(Reference.MOD_NAME);

	@Override
	public void onInitialize() {
		InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
	}

	public static void debug(String message, Object... args) {
		if (Configs.Generic.DEBUG_LOG.getBooleanValue()) {
			LOGGER.info("[debug] " + message, args);
		}
	}
}
