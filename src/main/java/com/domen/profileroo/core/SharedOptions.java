package com.domen.profileroo.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Options that stay the same in every profile. MaLiLib configs are {@code {"Category": {"option": value}}}, so a line is
 * {@code mod:Category.option} with {@code *}/{@code ?} masks; {@code mod:Category} and {@code mod} mean everything below.
 * A line starting with {@code !} takes options back out. Lines are applied top to bottom, the last match wins:
 * <pre>
 * tweakeroo:Generic.*         — all of Tweakeroo's Generic tab is shared
 * !tweakeroo:Generic.fastPlacementProtection
 * *:TweakHotkeys              — the same binds everywhere, only the toggles differ
 * </pre>
 * Lines starting with {@code #} are comments.
 */
public final class SharedOptions {
	private static final SharedOptions EMPTY = new SharedOptions(List.of());

	private final List<Rule> rules;

	private SharedOptions(List<Rule> rules) {
		this.rules = rules;
	}

	public static SharedOptions parse(Collection<String> lines) {
		List<Rule> rules = new ArrayList<>();
		for (String raw : lines) {
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			boolean negate = line.startsWith("!");
			if (negate) {
				line = line.substring(1).trim();
			}
			int colon = line.indexOf(':');
			String mod = colon < 0 ? line : line.substring(0, colon);
			String rest = colon < 0 ? "" : line.substring(colon + 1);
			int dot = rest.indexOf('.');
			String category = dot < 0 ? rest : rest.substring(0, dot);
			String option = dot < 0 ? "" : rest.substring(dot + 1);
			rules.add(new Rule(negate, glob(mod), glob(category), glob(option)));
		}
		return rules.isEmpty() ? EMPTY : new SharedOptions(List.copyOf(rules));
	}

	private static Pattern glob(String mask) {
		return Glob.compile(mask.trim().isEmpty() ? "*" : mask.trim());
	}

	public boolean isEmpty() {
		return this.rules.isEmpty();
	}

	/** Whether any line can share something of this mod: when none can, its files are copied byte for byte. */
	public boolean affects(String modId) {
		for (Rule rule : this.rules) {
			if (!rule.negate && rule.mod.matcher(modId).matches()) {
				return true;
			}
		}
		return false;
	}

	public boolean isShared(String modId, String category, String option) {
		boolean shared = false;
		for (Rule rule : this.rules) {
			if (rule.mod.matcher(modId).matches() && rule.category.matcher(category).matches() && rule.option.matcher(option).matches()) {
				shared = !rule.negate;
			}
		}
		return shared;
	}

	/**
	 * The profile's config ({@code incoming}) with every shared option taken from the config that is in use now
	 * ({@code live}). Options the live config does not have are left as the profile has them.
	 */
	public JsonObject merge(String modId, JsonObject incoming, JsonObject live) {
		JsonObject result = incoming.deepCopy();
		for (Map.Entry<String, JsonElement> categoryEntry : live.entrySet()) {
			if (!categoryEntry.getValue().isJsonObject()) {
				continue;
			}
			String category = categoryEntry.getKey();
			JsonObject target = result.get(category) instanceof JsonObject existing ? existing : null;
			for (Map.Entry<String, JsonElement> option : categoryEntry.getValue().getAsJsonObject().entrySet()) {
				if (this.isShared(modId, category, option.getKey())) {
					if (target == null) {
						target = new JsonObject();
						result.add(category, target);
					}
					target.add(option.getKey(), option.getValue().deepCopy());
				}
			}
		}
		return result;
	}

	private record Rule(boolean negate, Pattern mod, Pattern category, Pattern option) {
	}
}
