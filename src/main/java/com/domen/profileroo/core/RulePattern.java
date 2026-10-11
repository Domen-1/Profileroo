package com.domen.profileroo.core;

import java.util.Locale;

/**
 * Server rule patterns, checked against {@link SessionInfo}:
 * <ul>
 *     <li>{@code play.example.com} — this host on any port; {@code play.example.com:25566} — host and port;</li>
 *     <li>{@code *.example.com}, {@code 192.168.1.*}, {@code *} — {@code *} and {@code ?} work in the host and the port;</li>
 *     <li>{@code name:<mask>} — by the server's name in the server list;</li>
 *     <li>{@code sp} — any singleplayer world, {@code sp:<mask>} — by world name or folder;</li>
 *     <li>{@code lan} — any LAN game; {@code realms} or {@code realms:<mask>} — Realms.</li>
 * </ul>
 * Case is ignored everywhere.
 */
public final class RulePattern {
	private RulePattern() {
	}

	public static boolean matches(String pattern, SessionInfo session) {
		String text = pattern.trim();
		if (text.isEmpty()) {
			return false;
		}
		String lower = text.toLowerCase(Locale.ROOT);
		SessionInfo.Kind kind = session.kind();

		if (lower.equals("sp") || lower.equals("singleplayer")) {
			return kind == SessionInfo.Kind.SINGLEPLAYER;
		}
		if (lower.startsWith("sp:")) {
			String mask = text.substring(3);
			return kind == SessionInfo.Kind.SINGLEPLAYER
					&& (Glob.matches(mask, session.worldName()) || Glob.matches(mask, session.worldFolder()));
		}
		if (lower.equals("lan")) {
			return kind == SessionInfo.Kind.LAN;
		}
		if (lower.equals("realms")) {
			return kind == SessionInfo.Kind.REALMS;
		}
		if (lower.startsWith("realms:")) {
			return kind == SessionInfo.Kind.REALMS && Glob.matches(text.substring(7), session.serverName());
		}
		if (lower.startsWith("name:")) {
			return kind != SessionInfo.Kind.SINGLEPLAYER && Glob.matches(text.substring(5), session.serverName());
		}
		if (kind != SessionInfo.Kind.MULTIPLAYER && kind != SessionInfo.Kind.LAN) {
			return false;
		}

		SessionInfo.HostPort target = SessionInfo.HostPort.parse(session.address());
		String hostMask = lower;
		String portMask = null;
		if (lower.startsWith("[")) {
			int end = lower.indexOf(']');
			if (end > 0) {
				hostMask = lower.substring(1, end);
				String rest = lower.substring(end + 1);
				portMask = rest.startsWith(":") ? rest.substring(1) : null;
			}
		} else {
			int colon = lower.indexOf(':');
			if (colon >= 0 && colon == lower.lastIndexOf(':')) {
				hostMask = lower.substring(0, colon);
				portMask = lower.substring(colon + 1);
			}
		}
		while (hostMask.endsWith(".")) {
			hostMask = hostMask.substring(0, hostMask.length() - 1);
		}
		return Glob.matches(hostMask, target.host())
				&& (portMask == null || portMask.isEmpty() || Glob.matches(portMask, Integer.toString(target.port())));
	}
}
