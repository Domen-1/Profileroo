package com.domen.profileroo.core;

import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Profile names are folder names and command arguments at the same time, so they are kept to letters, digits and
 * {@code _ . + -}: no spaces, nothing a file system or the chat command splitter would choke on.
 */
public final class ProfileNames {
	public static final int MAX_LENGTH = 32;
	private static final Pattern ALLOWED = Pattern.compile("[\\p{L}\\p{N}_.+\\-]+");
	private static final Set<String> RESERVED = Set.of(
			"con", "prn", "aux", "nul",
			"com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
			"lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

	private ProfileNames() {
	}

	/** Null when the name can be used, otherwise the translation key that explains why not. */
	@Nullable
	public static String problem(String name) {
		if (name.isEmpty()) {
			return "profileroo.message.error.name_empty";
		}
		if (name.length() > MAX_LENGTH) {
			return "profileroo.message.error.name_too_long";
		}
		if (!ALLOWED.matcher(name).matches() || name.startsWith(".") || name.endsWith(".")) {
			return "profileroo.message.error.name_chars";
		}
		String base = name.toLowerCase(Locale.ROOT);
		int dot = base.indexOf('.');
		if (RESERVED.contains(dot >= 0 ? base.substring(0, dot) : base)) {
			return "profileroo.message.error.name_reserved";
		}
		return null;
	}

	/** {@code base}, or {@code base-2}, {@code base-3}... — the first one that is not taken. */
	public static String unique(String base, Predicate<String> taken) {
		if (!taken.test(base)) {
			return base;
		}
		for (int i = 2; ; i++) {
			String suffix = "-" + i;
			String head = base.length() + suffix.length() > MAX_LENGTH ? base.substring(0, MAX_LENGTH - suffix.length()) : base;
			String candidate = head + suffix;
			if (!taken.test(candidate)) {
				return candidate;
			}
		}
	}
}
