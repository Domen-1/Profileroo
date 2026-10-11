package com.domen.profileroo.mixin;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.IConfigHandler;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ConfigManager.class)
public interface ConfigManagerAccessor {
	@Accessor("configHandlers")
	Map<String, IConfigHandler> profileroo$getConfigHandlers();
}
