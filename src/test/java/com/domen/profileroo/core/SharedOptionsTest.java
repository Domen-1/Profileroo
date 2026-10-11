package com.domen.profileroo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class SharedOptionsTest {
	@Test
	void lastMatchingLineWins() {
		SharedOptions shared = SharedOptions.parse(List.of(
				"# comment",
				"tweakeroo:Generic.*",
				"!tweakeroo:Generic.fastPlacement*",
				"*:TweakHotkeys",
				"minihud"));
		assertTrue(shared.isShared("tweakeroo", "Generic", "hotbarSlotCycleMax"));
		assertFalse(shared.isShared("tweakeroo", "Generic", "fastPlacementProtection"));
		assertTrue(shared.isShared("tweakeroo", "TweakHotkeys", "tweakFastPlace"));
		assertFalse(shared.isShared("tweakeroo", "TweakToggles", "tweakFastPlace"));
		assertTrue(shared.isShared("litematica", "TweakHotkeys", "x"));
		assertTrue(shared.isShared("minihud", "Colors", "anything"));
		assertTrue(shared.affects("minihud"));
		assertTrue(shared.affects("itemscroller"));
		assertTrue(SharedOptions.parse(List.of("", "  ", "# only comments")).isEmpty());
		assertFalse(SharedOptions.parse(List.of("tweakeroo")).affects("minihud"));
	}

	@Test
	void mergeTakesSharedValuesFromLive() {
		SharedOptions shared = SharedOptions.parse(List.of("tweakeroo:TweakHotkeys", "tweakeroo:Generic.shared"));
		JsonObject profile = JsonParser.parseString("""
				{"TweakToggles": {"tweakFastPlace": true},
				 "TweakHotkeys": {"tweakFastPlace": {"keys": "F"}},
				 "Generic": {"shared": 1, "own": 1},
				 "config_version": 3}
				""").getAsJsonObject();
		JsonObject live = JsonParser.parseString("""
				{"TweakToggles": {"tweakFastPlace": false},
				 "TweakHotkeys": {"tweakFastPlace": {"keys": "G"}, "tweakNew": {"keys": "H"}},
				 "Generic": {"shared": 2, "own": 2}}
				""").getAsJsonObject();
		JsonObject merged = shared.merge("tweakeroo", profile, live);
		assertTrue(merged.getAsJsonObject("TweakToggles").get("tweakFastPlace").getAsBoolean(), "toggles follow the profile");
		assertEquals("G", merged.getAsJsonObject("TweakHotkeys").getAsJsonObject("tweakFastPlace").get("keys").getAsString());
		assertEquals("H", merged.getAsJsonObject("TweakHotkeys").getAsJsonObject("tweakNew").get("keys").getAsString());
		assertEquals(2, merged.getAsJsonObject("Generic").get("shared").getAsInt());
		assertEquals(1, merged.getAsJsonObject("Generic").get("own").getAsInt());
		assertEquals(3, merged.get("config_version").getAsInt());
		assertEquals("F", profile.getAsJsonObject("TweakHotkeys").getAsJsonObject("tweakFastPlace").get("keys").getAsString(), "input untouched");
	}

	@Test
	void mergeAddsMissingCategory() {
		SharedOptions shared = SharedOptions.parse(List.of("mod:Colors"));
		JsonObject merged = shared.merge("mod",
				JsonParser.parseString("{\"Generic\": {}}").getAsJsonObject(),
				JsonParser.parseString("{\"Colors\": {\"a\": \"#FF00FF00\"}}").getAsJsonObject());
		assertEquals("#FF00FF00", merged.getAsJsonObject("Colors").get("a").getAsString());
	}
}
