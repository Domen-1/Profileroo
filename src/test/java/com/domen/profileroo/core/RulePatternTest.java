package com.domen.profileroo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RulePatternTest {
	private static final SessionInfo SMP = SessionInfo.multiplayer("Play.Example.com", "Our SMP");
	private static final SessionInfo SMP_PORT = SessionInfo.multiplayer("play.example.com:25566", "SMP test");
	private static final SessionInfo IP = SessionInfo.multiplayer("192.168.1.20", "home");
	private static final SessionInfo IPV6 = SessionInfo.multiplayer("[::1]:25570", "local");
	private static final SessionInfo WORLD = SessionInfo.singleplayer("Креатив тест", "Kreativ test");
	private static final SessionInfo LAN = SessionInfo.lan("192.168.1.5:41234", "LAN World");
	private static final SessionInfo REALM = SessionInfo.realms("Friends Realm");

	@Test
	void hostWithoutPortMatchesAnyPort() {
		assertTrue(RulePattern.matches("play.example.com", SMP));
		assertTrue(RulePattern.matches("play.example.com", SMP_PORT));
		assertTrue(RulePattern.matches("PLAY.EXAMPLE.COM.", SMP));
		assertFalse(RulePattern.matches("example.com", SMP));
	}

	@Test
	void portIsCheckedWhenGiven() {
		assertTrue(RulePattern.matches("play.example.com:25565", SMP));
		assertFalse(RulePattern.matches("play.example.com:25565", SMP_PORT));
		assertTrue(RulePattern.matches("play.example.com:25566", SMP_PORT));
		assertTrue(RulePattern.matches("play.example.com:2556?", SMP_PORT));
		assertTrue(RulePattern.matches("*:25566", SMP_PORT));
	}

	@Test
	void masks() {
		assertTrue(RulePattern.matches("*.example.com", SMP));
		assertTrue(RulePattern.matches("192.168.1.*", IP));
		assertFalse(RulePattern.matches("192.168.2.*", IP));
		assertTrue(RulePattern.matches("*", SMP));
		assertTrue(RulePattern.matches("*", LAN));
		assertFalse(RulePattern.matches("*", WORLD));
		assertFalse(RulePattern.matches("*", REALM));
		assertFalse(RulePattern.matches("", SMP));
		assertFalse(RulePattern.matches("   ", SMP));
	}

	@Test
	void ipv6() {
		assertTrue(RulePattern.matches("[::1]:25570", IPV6));
		assertTrue(RulePattern.matches("[::1]", IPV6));
		assertTrue(RulePattern.matches("::1", IPV6));
		assertFalse(RulePattern.matches("[::1]:25565", IPV6));
	}

	@Test
	void byServerListName() {
		assertTrue(RulePattern.matches("name:our smp", SMP));
		assertTrue(RulePattern.matches("name:SMP*", SMP_PORT));
		assertFalse(RulePattern.matches("name:SMP*", SMP));
		assertFalse(RulePattern.matches("name:*", WORLD));
	}

	@Test
	void singleplayer() {
		assertTrue(RulePattern.matches("sp", WORLD));
		assertTrue(RulePattern.matches("singleplayer", WORLD));
		assertTrue(RulePattern.matches("sp:креатив*", WORLD));
		assertTrue(RulePattern.matches("sp:Kreativ test", WORLD));
		assertFalse(RulePattern.matches("sp:Survival", WORLD));
		assertFalse(RulePattern.matches("sp", SMP));
	}

	@Test
	void lanAndRealms() {
		assertTrue(RulePattern.matches("lan", LAN));
		assertFalse(RulePattern.matches("lan", SMP));
		assertTrue(RulePattern.matches("realms", REALM));
		assertTrue(RulePattern.matches("realms:friends*", REALM));
		assertFalse(RulePattern.matches("realms:other", REALM));
		assertFalse(RulePattern.matches("realms", SMP));
	}

	@Test
	void exactPatternMatchesItsOwnSession() {
		for (SessionInfo session : new SessionInfo[] {SMP, SMP_PORT, IP, IPV6, WORLD, LAN, REALM}) {
			assertTrue(RulePattern.matches(session.exactPattern(), session), session.toString());
		}
		assertEquals("play.example.com", SMP.exactPattern());
		assertEquals("play.example.com:25566", SMP_PORT.exactPattern());
		assertEquals("[::1]:25570", IPV6.exactPattern());
		assertEquals("sp:Kreativ test", WORLD.exactPattern());
	}

	@Test
	void sameAsIgnoresCaseAndDefaultPort() {
		assertTrue(SMP.sameAs(SessionInfo.multiplayer("play.example.com:25565", "renamed")));
		assertFalse(SMP.sameAs(SMP_PORT));
		assertFalse(SMP.sameAs(WORLD));
	}
}
