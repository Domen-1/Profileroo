package com.domen.profileroo.core;

import java.util.Locale;

/**
 * Where the player is: a server (by the address typed in the server list), a LAN game, a Realm or a singleplayer world.
 * Pure data, so the rule matching can be tested without a game.
 */
public record SessionInfo(Kind kind, String address, String serverName, String worldName, String worldFolder) {
	public enum Kind {
		SINGLEPLAYER,
		MULTIPLAYER,
		LAN,
		REALMS
	}

	public static SessionInfo multiplayer(String address, String serverName) {
		return new SessionInfo(Kind.MULTIPLAYER, address, serverName, "", "");
	}

	public static SessionInfo lan(String address, String serverName) {
		return new SessionInfo(Kind.LAN, address, serverName, "", "");
	}

	public static SessionInfo realms(String realmName) {
		return new SessionInfo(Kind.REALMS, "", realmName, "", "");
	}

	public static SessionInfo singleplayer(String worldName, String worldFolder) {
		return new SessionInfo(Kind.SINGLEPLAYER, "", "", worldName, worldFolder);
	}

	public boolean isSingleplayer() {
		return this.kind == Kind.SINGLEPLAYER;
	}

	/** A rule pattern that matches exactly this place: what "remember the choice" and "bind this server" add. */
	public String exactPattern() {
		return switch (this.kind) {
			case SINGLEPLAYER -> "sp:" + this.worldFolder;
			case REALMS -> "realms:" + this.serverName;
			case LAN -> "lan";
			case MULTIPLAYER -> HostPort.parse(this.address).toPattern();
		};
	}

	/** Short text for messages: the address, the world or the Realm name. */
	public String label() {
		return switch (this.kind) {
			case SINGLEPLAYER -> this.worldName.isEmpty() ? this.worldFolder : this.worldName;
			case REALMS -> this.serverName;
			case LAN, MULTIPLAYER -> HostPort.parse(this.address).toPattern();
		};
	}

	/** Same server or world, ignoring how the address was capitalised or whether the default port was written. */
	public boolean sameAs(SessionInfo other) {
		if (other.kind != this.kind) {
			return false;
		}
		return switch (this.kind) {
			case SINGLEPLAYER -> this.worldFolder.equals(other.worldFolder);
			case REALMS -> this.serverName.equals(other.serverName);
			case LAN, MULTIPLAYER -> HostPort.parse(this.address).equals(HostPort.parse(other.address));
		};
	}

	/** Host in lower case without a trailing dot, and the port (25565 when the address has none). */
	public record HostPort(String host, int port) {
		public static final int DEFAULT_PORT = 25565;

		public static HostPort parse(String address) {
			String text = address.trim().toLowerCase(Locale.ROOT);
			String host = text;
			int port = DEFAULT_PORT;
			if (text.startsWith("[")) {
				int end = text.indexOf(']');
				if (end > 0) {
					host = text.substring(1, end);
					String rest = text.substring(end + 1);
					if (rest.startsWith(":")) {
						port = parsePort(rest.substring(1));
					}
				}
			} else {
				int colon = text.indexOf(':');
				if (colon >= 0 && colon == text.lastIndexOf(':')) {
					host = text.substring(0, colon);
					port = parsePort(text.substring(colon + 1));
				}
			}
			while (host.endsWith(".")) {
				host = host.substring(0, host.length() - 1);
			}
			return new HostPort(host, port);
		}

		private static int parsePort(String text) {
			try {
				int port = Integer.parseInt(text.trim());
				return port > 0 && port <= 65535 ? port : DEFAULT_PORT;
			} catch (NumberFormatException e) {
				return DEFAULT_PORT;
			}
		}

		/** {@code host} for the default port, {@code host:port} otherwise; IPv6 hosts in brackets when a port follows. */
		public String toPattern() {
			if (this.port == DEFAULT_PORT) {
				return this.host;
			}
			return (this.host.contains(":") ? "[" + this.host + "]" : this.host) + ":" + this.port;
		}
	}
}
