package com.domen.profileroo.core;

import java.util.regex.Pattern;

/** {@code *} — any run of characters, {@code ?} — one character; case is ignored. */
public final class Glob {
	private Glob() {
	}

	public static Pattern compile(String glob) {
		StringBuilder regex = new StringBuilder();
		StringBuilder literal = new StringBuilder();
		for (int i = 0; i < glob.length(); i++) {
			char c = glob.charAt(i);
			if (c == '*' || c == '?') {
				if (!literal.isEmpty()) {
					regex.append(Pattern.quote(literal.toString()));
					literal.setLength(0);
				}
				regex.append(c == '*' ? ".*" : ".");
			} else {
				literal.append(c);
			}
		}
		if (!literal.isEmpty()) {
			regex.append(Pattern.quote(literal.toString()));
		}
		return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
	}

	public static boolean matches(String glob, String text) {
		return compile(glob).matcher(text).matches();
	}
}
