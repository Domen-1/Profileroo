package com.domen.profileroo.mixin;

import com.domen.profileroo.event.SessionTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaving the world for the configuration phase (a proxy moving the player to another backend) goes through
 * {@code clearClientLevel}, a real disconnect through {@code disconnect}. MaLiLib reports both as "world unloaded".
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "clearClientLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
	private void profileroo$onReconfiguration(Screen screen, CallbackInfo ci) {
		SessionTracker.onReconfiguration();
	}

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
	private void profileroo$onDisconnect(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
		SessionTracker.onDisconnect();
	}
}
