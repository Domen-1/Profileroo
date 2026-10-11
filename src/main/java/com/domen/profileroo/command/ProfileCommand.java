package com.domen.profileroo.command;

import com.domen.profileroo.core.ProfileState.ProfileData;
import com.domen.profileroo.core.ProfileState.Resolution;
import com.domen.profileroo.core.SessionInfo;
import com.domen.profileroo.gui.ConfigTab;
import com.domen.profileroo.profile.Messages;
import com.domen.profileroo.profile.OpResult;
import com.domen.profileroo.profile.ProfileManager;
import fi.dy.masa.malilib.interfaces.IClientCommandListener;
import fi.dy.masa.malilib.util.StringUtils;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * {@code #profile} in chat (MaLiLib client commands are sent from the chat box and never reach the server).
 * Profile names have no spaces, so plain splitting on whitespace is enough.
 */
public class ProfileCommand implements IClientCommandListener {
	public static final String COMMAND = "#profile";

	@Override
	public String getCommand() {
		return COMMAND;
	}

	@Override
	public boolean execute(List<String> args, Minecraft mc) {
		ProfileManager manager = ProfileManager.getInstance();
		manager.ensureReady();
		if (args.size() < 2) {
			this.status(manager);
			return true;
		}
		String sub = args.get(1).toLowerCase(Locale.ROOT);
		String a = args.size() > 2 ? args.get(2) : null;
		String b = args.size() > 3 ? args.get(3) : null;
		OpResult result = switch (sub) {
			case "list", "status" -> {
				this.status(manager);
				yield null;
			}
			case "help", "?" -> {
				this.help();
				yield null;
			}
			case "gui" -> {
				// The chat screen closes itself right after the command; a queued task runs after that.
				mc.schedule(() -> ConfigTab.PROFILES.open(null));
				yield null;
			}
			case "switch", "use" -> a == null ? usage("switch <name>") : manager.switchTo(a, ProfileManager.Cause.COMMAND);
			case "next" -> manager.cycle(1);
			case "prev", "previous" -> manager.cycle(-1);
			case "save" -> a == null ? manager.saveActive() : manager.overwrite(a);
			case "reset" -> manager.resetActive();
			case "create", "new" -> a == null ? usage("create <name>") : manager.create(a);
			case "copy" -> a == null || b == null ? usage("copy <from> <to>") : manager.duplicate(a, b);
			case "rename" -> a == null || b == null ? usage("rename <old> <new>") : manager.rename(a, b);
			case "delete", "remove" -> a == null ? usage("delete <name>") : manager.delete(a);
			case "lock" -> manager.setLocked(a == null ? manager.activeName() : a, true);
			case "unlock" -> manager.setLocked(a == null ? manager.activeName() : a, false);
			case "auto" -> this.auto(manager, a);
			case "bind" -> manager.bindCurrent(a);
			case "unbind" -> manager.unbindCurrent();
			// "#profile smp" is the short form of "#profile switch smp".
			default -> manager.switchTo(args.get(1), ProfileManager.Cause.COMMAND);
		};
		if (result != null) {
			Messages.chat(result);
		}
		return true;
	}

	/** {@code #profile auto} toggles, {@code #profile auto on|off} sets. */
	private OpResult auto(ProfileManager manager, @Nullable String arg) {
		if (arg == null) {
			return manager.setAutoSwitch(!manager.isAutoSwitch());
		}
		return switch (arg.toLowerCase(Locale.ROOT)) {
			case "on", "true", "вкл", "1" -> manager.setAutoSwitch(true);
			case "off", "false", "выкл", "0" -> manager.setAutoSwitch(false);
			default -> usage("auto [on|off]");
		};
	}

	private static OpResult usage(String usage) {
		return OpResult.error("profileroo.command.usage", COMMAND + " " + usage);
	}

	private void status(ProfileManager manager) {
		SessionInfo session = manager.session();
		MutableComponent list = Component.literal(StringUtils.translate("profileroo.command.profiles")).withStyle(ChatFormatting.GRAY);
		boolean first = true;
		for (ProfileData profile : manager.profiles()) {
			list.append(Component.literal(first ? " " : ", "));
			first = false;
			boolean active = profile.name.equals(manager.activeName());
			MutableComponent name = Component.literal(profile.name + (profile.locked ? " " + StringUtils.translate("profileroo.label.locked_tag") : ""));
			list.append(name.withStyle(active ? ChatFormatting.GREEN : ChatFormatting.WHITE));
		}
		Messages.chat(list);
		if (session != null) {
			Resolution resolution = manager.resolveCurrent();
			String by = resolution == null || resolution.rule() == null
					? StringUtils.translate("profileroo.label.no_rule")
					: StringUtils.translate("profileroo.label.reason_rule", resolution.rule().pattern);
			String profile = resolution == null || resolution.profile() == null ? "—" : resolution.profile();
			Messages.chat(Component.literal(StringUtils.translate("profileroo.command.here", session.label(), profile, by)).withStyle(ChatFormatting.GRAY));
		}
		if (!manager.isAutoSwitch()) {
			Messages.chat(Component.literal(StringUtils.translate("profileroo.command.auto_off_note", COMMAND)).withStyle(ChatFormatting.GOLD));
		}
		Messages.chat(Component.literal(StringUtils.translate("profileroo.command.help_hint", COMMAND)).withStyle(ChatFormatting.DARK_GRAY));
	}

	private void help() {
		for (String line : StringUtils.translate("profileroo.command.help").split("\n")) {
			Messages.chat(Component.literal(line).withStyle(ChatFormatting.GRAY));
		}
	}
}
