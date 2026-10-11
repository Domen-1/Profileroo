package com.domen.profileroo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.domen.profileroo.core.ProfileState.ProfileData;
import com.domen.profileroo.core.ProfileState.Resolution;
import com.domen.profileroo.core.ProfileState.ServerRule;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ProfileStateTest {
	private static ProfileState sample() {
		ProfileState state = ProfileState.defaults();
		state.profiles.add(new ProfileData("default"));
		state.profiles.add(new ProfileData("smp"));
		state.profiles.add(new ProfileData("cmp"));
		state.rules.add(new ServerRule("smp.example.com", "smp"));
		state.rules.add(new ServerRule("*.example.com", "cmp"));
		state.rules.add(new ServerRule("sp:Creative*", "cmp"));
		state.active = "default";
		return state;
	}

	@Test
	void firstMatchingRuleWins() {
		ProfileState state = sample();
		Resolution smp = state.resolve(SessionInfo.multiplayer("smp.example.com", ""));
		assertEquals("smp", smp.profile());
		assertEquals(0, smp.ruleIndex());
		assertEquals("cmp", state.resolve(SessionInfo.multiplayer("cmp.example.com", "")).profile());
		assertEquals("cmp", state.resolve(SessionInfo.singleplayer("Creative 2", "Creative 2")).profile());
	}

	@Test
	void fallbacksPerKind() {
		ProfileState state = sample();
		state.fallbackSingleplayer = "";
		Resolution other = state.resolve(SessionInfo.multiplayer("hypixel.net", ""));
		assertTrue(other.isFallback());
		assertEquals("default", other.profile());
		assertNull(state.resolve(SessionInfo.singleplayer("Survival", "Survival")).profile(), "empty fallback keeps the current profile");
		assertEquals("default", state.resolve(SessionInfo.realms("x")).profile());
	}

	@Test
	void renameAndRemoveReferences() {
		ProfileState state = sample();
		state.fallbackMultiplayer = "cmp";
		state.active = "cmp";
		state.renameReferences("CMP", "creative");
		assertEquals("creative", state.rules.get(1).profile);
		assertEquals("creative", state.rules.get(2).profile);
		assertEquals("creative", state.fallbackMultiplayer);
		assertEquals("creative", state.active);
		assertEquals(2, state.removeReferences("creative"));
		assertEquals(1, state.rules.size());
		assertEquals("", state.fallbackMultiplayer);
	}

	@Test
	void findIgnoresCase() {
		ProfileState state = sample();
		assertSame(state.profiles.get(1), state.find("SMP"));
		assertNull(state.find("nope"));
		assertNull(state.find(null));
	}

	@Test
	void jsonRoundTrip() {
		ProfileState state = sample();
		state.profiles.get(1).locked = true;
		state.profiles.get(1).hotkey = JsonParser.parseString("{\"keys\": \"LEFT_ALT,1\"}").getAsJsonObject();
		state.pendingApply = "smp";
		state.excludedMods.add("litematica");
		ProfileState copy = ProfileState.fromJson(state.toJson());
		assertEquals(state.toJson(), copy.toJson());
		assertTrue(copy.find("smp").locked);
		assertEquals("LEFT_ALT,1", copy.find("smp").hotkey.get("keys").getAsString());
		assertTrue(copy.isExcluded("Litematica"));
	}

	@Test
	void brokenEntriesAreSkipped() {
		JsonObject root = JsonParser.parseString("""
				{"active": "a",
				 "profiles": [{"name": "a"}, {"name": "a"}, {"name": "bad name"}, {"nope": 1}, 5, {"name": "b", "locked": "yes?"}],
				 "rules": [{"pattern": "x", "profile": ""}, {"pattern": "y", "profile": "b"}, "junk"]}
				""").getAsJsonObject();
		ProfileState state = ProfileState.fromJson(root);
		assertEquals(2, state.profiles.size());
		assertNotNull(state.find("b"));
		assertEquals(1, state.rules.size());
		assertTrue(state.isExcluded("malilib"), "missing excludedMods falls back to the default");
	}

	@Test
	void names() {
		assertNull(ProfileNames.problem("smp"));
		assertNull(ProfileNames.problem("Креатив_2.0+test"));
		assertNotNull(ProfileNames.problem(""));
		assertNotNull(ProfileNames.problem("with space"));
		assertNotNull(ProfileNames.problem("../evil"));
		assertNotNull(ProfileNames.problem(".hidden"));
		assertNotNull(ProfileNames.problem("ends."));
		assertNotNull(ProfileNames.problem("CON"));
		assertNotNull(ProfileNames.problem("com1.txt"));
		assertNotNull(ProfileNames.problem("a".repeat(ProfileNames.MAX_LENGTH + 1)));
		Predicate<String> taken = n -> n.equals("smp") || n.equals("smp-2");
		assertEquals("smp-3", ProfileNames.unique("smp", taken));
		assertEquals("cmp", ProfileNames.unique("cmp", taken));
		String longName = "x".repeat(ProfileNames.MAX_LENGTH);
		assertEquals(ProfileNames.MAX_LENGTH, ProfileNames.unique(longName, n -> n.equals(longName)).length());
	}
}
