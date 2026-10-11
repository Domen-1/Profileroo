package com.domen.profileroo.test;

import com.domen.profileroo.config.Configs;
import com.domen.profileroo.core.ProfileState.ServerRule;
import com.domen.profileroo.core.RulePattern;
import com.domen.profileroo.core.SessionInfo;
import com.domen.profileroo.gui.ConfigTab;
import com.domen.profileroo.profile.OpResult;
import com.domen.profileroo.profile.ProfileManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fi.dy.masa.malilib.command.ClientCommandHandler;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeybindMulti;
import fi.dy.masa.tweakeroo.config.FeatureToggle;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * End to end in a real client with Tweakeroo and MiniHUD: profiles made at the title screen, a dedicated server joined
 * over the network (rule, hotkey, chat command, locked profile, remembered choice) and a singleplayer world. Screenshots
 * of the screens go to build/run/clientGameTest/screenshots.
 */
public class ProfilerooClientGameTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("profileroo-test");
	/** "Fast block placement" stands for the survival server, "fly speed" for the creative one. */
	private static final FeatureToggle SMP_TWEAK = FeatureToggle.TWEAK_FAST_BLOCK_PLACEMENT;
	private static final FeatureToggle CMP_TWEAK = FeatureToggle.TWEAK_FLY_SPEED;

	@Override
	public void runTest(ClientGameTestContext context) {
		setUpClient(context);
		titleScreen(context);
		dedicatedServer(context);
		// Closing a singleplayer world hangs inside the test framework, so it goes last.
		singleplayer(context);
	}

	private static void setUpClient(ClientGameTestContext context) {
		context.getInput().resizeWindow(1600, 900);
		AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
		context.runOnClient(mc -> {
			mc.options.guiScale().set(2);
			mc.options.languageCode = "ru_ru";
			mc.getLanguageManager().setSelected("ru_ru");
			reload.set(mc.reloadResourcePacks());
		});
		context.waitFor(mc -> reload.get().isDone(), 1200);
		context.waitTicks(20);
	}

	private static void titleScreen(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			ProfileManager manager = ProfileManager.getInstance();
			manager.ensureReady();
			check(manager.activeName().equals("default"), "the first start makes profile 'default' from the current configs");
			check(manager.managedMods().containsAll(List.of("tweakeroo", "minihud")), "Tweakeroo and MiniHUD are found through MaLiLib");
			check(!manager.managedMods().contains("malilib"), "MaLiLib's own config is left alone by default");
			check(manager.knownFiles("tweakeroo").getFirst().endsWith("tweakeroo.json"), "Tweakeroo's file is found by watching its save");
			check(manager.storedMods("default").containsAll(Set.of("tweakeroo", "minihud")), "'default' holds both mods");
			check(Files.isDirectory(manager.store().root().resolve("backups")), "the first start backs up the configs");

			SMP_TWEAK.setBooleanValue(false);
			CMP_TWEAK.setBooleanValue(false);
			ok(manager.saveActive(), "save default");
			ok(manager.create("smp"), "create smp");
			ok(manager.create("cmp"), "create cmp");

			ok(manager.switchTo("smp", ProfileManager.Cause.MANUAL), "switch to smp");
			SMP_TWEAK.setBooleanValue(true);
			SMP_TWEAK.getKeybind().setValueFromString("G");
			ok(manager.switchTo("cmp", ProfileManager.Cause.MANUAL), "switch to cmp");
			check(!SMP_TWEAK.getBooleanValue() && SMP_TWEAK.getKeybind().getStringValue().isEmpty(), "cmp does not have smp's toggle and bind");
			check(storedToggle("smp", SMP_TWEAK), "smp's folder got the toggle when switching away from it");
			CMP_TWEAK.setBooleanValue(true);

			ok(manager.switchTo("default", ProfileManager.Cause.MANUAL), "switch to default");
			check(!SMP_TWEAK.getBooleanValue() && !CMP_TWEAK.getBooleanValue(), "default has neither");
			ok(manager.switchTo("smp", ProfileManager.Cause.MANUAL), "switch to smp again");
			check(SMP_TWEAK.getBooleanValue() && !CMP_TWEAK.getBooleanValue(), "smp comes back with its toggle");
			check(SMP_TWEAK.getKeybind().getStringValue().equals("G"), "smp comes back with its bind");
			ok(manager.switchTo("cmp", ProfileManager.Cause.MANUAL), "switch to cmp again");
			check(CMP_TWEAK.getBooleanValue() && !SMP_TWEAK.getBooleanValue(), "cmp comes back with its toggle");

			// Shared options: binds the same everywhere, toggles per profile.
			Configs.Generic.SHARED_OPTIONS.setStrings(List.of("tweakeroo:TweakHotkeys"));
			SMP_TWEAK.getKeybind().setValueFromString("H");
			ok(manager.switchTo("smp", ProfileManager.Cause.MANUAL), "switch to smp with shared binds");
			check(SMP_TWEAK.getBooleanValue(), "the toggle still follows the profile");
			check(SMP_TWEAK.getKeybind().getStringValue().equals("H"), "the shared bind stays as it was");
			Configs.Generic.SHARED_OPTIONS.setStrings(List.of());

			ok(manager.switchTo("default", ProfileManager.Cause.MANUAL), "back to default");
			check(manager.rules().isEmpty(), "switching at the title screen remembers nothing");
		});
	}

	private static void dedicatedServer(ClientGameTestContext context) {
		Properties properties = new Properties();
		properties.setProperty("level-type", "minecraft:flat");
		properties.setProperty("enforce-secure-profile", "false");
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties)) {
			context.runOnClient(mc -> {
				ProfileManager.getInstance().addRule("localhost", "smp");
				ProfileManager.getInstance().addRule("sp:Креатив*", "cmp");
			});

			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				context.waitTicks(20);
				context.runOnClient(mc -> {
					ProfileManager manager = ProfileManager.getInstance();
					SessionInfo session = manager.session();
					check(session != null && session.kind() == SessionInfo.Kind.MULTIPLAYER, "joined a server: " + session);
					check(manager.activeName().equals("smp"), "the rule 'localhost' switched to smp on join");
					check(SMP_TWEAK.getBooleanValue() && SMP_TWEAK.getKeybind().getStringValue().equals("H"),
							"Tweakeroo loaded smp's settings in the same join");
				});
				shot(context, "ingame-join-message");

				context.runOnClient(mc -> {
					ProfileManager manager = ProfileManager.getInstance();
					ok(manager.switchTo("default", ProfileManager.Cause.MANUAL), "manual switch on the server");
					check(!SMP_TWEAK.getBooleanValue(), "default's settings in game");
					check(manager.rules().size() == 2, "a server with a rule is not remembered over the rule");
					ok(manager.switchTo("smp", ProfileManager.Cause.MANUAL), "back to smp");

					ConfigHotkey cmpHotkey = profileHotkey("cmp");
					cmpHotkey.getKeybind().setValueFromString("F8");
					ProfileManager.getInstance().saveState();
					InputEventHandler.getKeybindManager().updateUsedKeys();
				});
				context.runOnClient(mc -> {
					IKeybind keybind = profileHotkey("cmp").getKeybind();
					boolean registered = InputEventHandler.getKeybindManager().getKeybindCategories().stream()
							.anyMatch(category -> category.getHotkeys().stream().anyMatch(h -> h.getKeybind() == keybind));
					check(registered && keybind.getKeys().size() == 1, "the profile hotkey is registered with MaLiLib");
					// Keys simulated by the client game test never reach MaLiLib (Tweakeroo's own hotkeys do not fire
					// either), so the bind's callback is called the way MaLiLib calls it.
					try {
						Field field = KeybindMulti.class.getDeclaredField("callback");
						field.setAccessible(true);
						((IHotkeyCallback) field.get(keybind)).onKeyAction(KeyAction.PRESS, keybind);
					} catch (ReflectiveOperationException e) {
						throw new AssertionError(e);
					}
				});
				context.waitTicks(5);
				context.runOnClient(mc -> {
					check(ProfileManager.getInstance().activeName().equals("cmp"), "the profile hotkey switched to cmp");
					check(CMP_TWEAK.getBooleanValue(), "cmp's settings in game after the hotkey");
				});
				shot(context, "ingame-hotkey-actionbar");

				context.runOnClient(mc -> {
					mc.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.empty(), false);
					check(ClientCommandHandler.INSTANCE.onSendClientMessage("#profile smp", mc), "#profile is a client command");
					check(ProfileManager.getInstance().activeName().equals("smp"), "#profile smp switched to smp");
					ClientCommandHandler.INSTANCE.onSendClientMessage("#profile", mc);
				});
				context.waitTicks(5);
				shot(context, "ingame-command-status");
				context.runOnClient(mc -> ClientCommandHandler.INSTANCE.onSendClientMessage("#profile gui", mc));
				context.waitTicks(3);
				context.runOnClient(mc -> check(mc.gui.screen() instanceof com.domen.profileroo.gui.GuiProfiles, "#profile gui opens the profiles screen"));
				context.setScreen(() -> null);

				context.runOnClient(mc -> {
					ProfileManager manager = ProfileManager.getInstance();
					ok(manager.setLocked("smp", true), "lock smp");
					SMP_TWEAK.setBooleanValue(false);
				});
				screens(context);
			}

			context.runOnClient(mc -> {
				check(storedToggle("smp", SMP_TWEAK), "leaving did not save the change into the locked profile");
				check(!SMP_TWEAK.getBooleanValue(), "the change is still in the game until the next join");
			});
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				context.runOnClient(mc -> check(SMP_TWEAK.getBooleanValue(), "joining again brought the locked profile's settings back"));
			}

			// No rule and "keep current" for other servers: a manual switch gets remembered.
			context.runOnClient(mc -> {
				ProfileManager manager = ProfileManager.getInstance();
				ok(manager.setLocked("smp", false), "unlock smp");
				manager.removeRule(0);
				manager.setFallback(false, "");
			});
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				context.runOnClient(mc -> {
					ProfileManager manager = ProfileManager.getInstance();
					check(manager.activeName().equals("smp"), "'keep current' kept smp");
					OpResult result = manager.switchTo("cmp", ProfileManager.Cause.MANUAL);
					check(result.key().equals("profileroo.message.switched_remembered"), "the choice was remembered: " + result.text());
					ServerRule rule = manager.rules().getLast();
					check(rule.profile.equals("cmp") && RulePattern.matches(rule.pattern, manager.session()), "remembered rule " + rule.pattern);
					ok(manager.switchTo("default", ProfileManager.Cause.MANUAL), "switch to default");
				});
			}
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				context.runOnClient(mc -> check(ProfileManager.getInstance().activeName().equals("cmp"), "the remembered rule works on the next join"));
			}
			context.runOnClient(mc -> ProfileManager.getInstance().setFallback(false, "default"));
		}
	}

	private static void singleplayer(ClientGameTestContext context) {
		context.runOnClient(mc -> ProfileManager.getInstance().addRule("sp", "default"));
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.waitTicks(20);
			context.runOnClient(mc -> {
				ProfileManager manager = ProfileManager.getInstance();
				SessionInfo session = manager.session();
				check(session != null && session.isSingleplayer() && !session.worldFolder().isEmpty(), "singleplayer world seen: " + session);
				check(manager.activeName().equals("default"), "rule 'sp' switched to default");
			});
			shot(context, "singleplayer-join");
			LOG.info("Profileroo client test passed");
		}
	}

	/** The mod's screens, opened over the game on the server, with the status line showing what applies here. */
	/**
	 * The mod's screens, opened over the game on the server, so the status line shows what applies here. A few more
	 * profiles, rules and binds make them look like a real setup; they are taken back out afterwards.
	 */
	private static void screens(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			ProfileManager manager = ProfileManager.getInstance();
			ok(manager.create("build"), "create build");
			ok(manager.setLocked("build", true), "lock build");
			manager.addRule("smp.example.net", "smp");
			manager.addRule("*.creative.example.net", "cmp");
			manager.addRule("name:Анархия*", "build");
			profileHotkey("default").getKeybind().setValueFromString("LEFT_ALT,1");
			profileHotkey("smp").getKeybind().setValueFromString("LEFT_ALT,2");
			profileHotkey("build").getKeybind().setValueFromString("LEFT_ALT,4");
			Configs.Hotkeys.NEXT_PROFILE.getKeybind().setValueFromString("LEFT_ALT,P");
			Configs.Generic.SHARED_OPTIONS.setStrings(List.of("*:TweakHotkeys", "minihud:Colors"));
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		for (ConfigTab tab : ConfigTab.values()) {
			context.setScreen(() -> tab.create(null));
			context.waitTicks(3);
			shot(context, "screen-" + tab.name().toLowerCase());
		}
		context.setScreen(() -> null);
		context.runOnClient(mc -> {
			ProfileManager manager = ProfileManager.getInstance();
			for (int i = 0; i < 3; i++) {
				manager.removeRule(manager.rules().size() - 1);
			}
			Configs.Generic.SHARED_OPTIONS.setStrings(List.of());
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		context.waitTicks(2);
	}

	/** A screenshot without the toasts the test server causes ("chat messages cannot be verified" and the like). */
	private static void shot(ClientGameTestContext context, String name) {
		context.runOnClient(mc -> mc.gui.toastManager().clear());
		context.waitTicks(1);
		context.takeScreenshot(name);
	}

	private static ConfigHotkey profileHotkey(String profile) {
		for (ConfigHotkey hotkey : ProfileManager.getInstance().profileHotkeys()) {
			if (hotkey.getName().equals(profile)) {
				return hotkey;
			}
		}
		throw new AssertionError("no hotkey for profile " + profile);
	}

	private static boolean storedToggle(String profile, FeatureToggle toggle) {
		Path file = ProfileManager.getInstance().store().profileDir(profile).resolve("tweakeroo/tweakeroo.json");
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			return root.getAsJsonObject("TweakToggles").get(toggle.getName()).getAsBoolean();
		} catch (IOException e) {
			throw new AssertionError("cannot read " + file, e);
		}
	}

	private static void ok(OpResult result, String what) {
		check(result.ok(), what + " -> " + result.text());
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("Profileroo test failed: " + what);
		}
		LOG.info("ok: {}", what);
	}
}
