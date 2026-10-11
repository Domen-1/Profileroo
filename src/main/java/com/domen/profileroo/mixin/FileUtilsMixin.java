package com.domen.profileroo.mixin;

import com.domen.profileroo.core.ConfigFileTracker;
import fi.dy.masa.malilib.config.value.FileWriteType;
import fi.dy.masa.malilib.util.FileUtils;
import java.io.BufferedWriter;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FileUtils.class)
public abstract class FileUtilsMixin {
	@Inject(method = "writeDataToFile", at = @At("HEAD"))
	private static void profileroo$onWriteDataToFile(Path file, Consumer<BufferedWriter> dataWriter, FileWriteType writeType,
			CallbackInfoReturnable<Boolean> cir) {
		ConfigFileTracker.onWrite(file);
	}
}
