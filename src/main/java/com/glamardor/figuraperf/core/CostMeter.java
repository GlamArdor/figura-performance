package com.glamardor.figuraperf.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Times each avatar, keeping scripts and geometry apart.
 *
 * <p>The split is the point: one number per avatar says an avatar is expensive, two say why. If the
 * scripts dominate, thinning ticks is the answer and Figura's renderer can stay as it is; if the
 * geometry dominates, no amount of thinning will help and only a rewritten renderer would.
 *
 * <p>Kept as a smoothed average rather than a raw reading – a single frame bounces around far too
 * much to read off the screen, and what matters is which avatar is expensive all the time.
 */
public final class CostMeter {

	private static final Map<UUID, Sample> SAMPLES = new ConcurrentHashMap<>();

	private CostMeter() {
	}

	/** Lua ticks, script events and Blockbench animations. */
	public static void recordScript(UUID owner, long nanos) {
		if (owner != null)
			SAMPLES.computeIfAbsent(owner, key -> new Sample()).pendingScript += nanos;
	}

	/** Walking the part tree and refilling the vertex buffer. */
	public static void recordRender(UUID owner, long nanos) {
		if (owner != null)
			SAMPLES.computeIfAbsent(owner, key -> new Sample()).pendingRender += nanos;
	}

	/** Called once per frame: what was gathered becomes this frame's reading. */
	public static void endFrame() {
		for (Sample sample : SAMPLES.values())
			sample.roll();
	}

	public static double totalFor(UUID owner) {
		Sample sample = SAMPLES.get(owner);
		return sample == null ? 0d : (sample.script + sample.render) / 1_000_000d;
	}

	public static List<Row> top(int count) {
		List<Row> rows = new ArrayList<>();
		for (Map.Entry<UUID, Sample> entry : SAMPLES.entrySet()) {
			Sample sample = entry.getValue();
			rows.add(new Row(entry.getKey(), sample.script / 1_000_000d, sample.render / 1_000_000d));
		}

		rows.sort(Comparator.comparingDouble(Row::total).reversed());
		return rows.size() > count ? rows.subList(0, count) : rows;
	}

	public static double totalScriptMillis() {
		double total = 0d;
		for (Sample sample : SAMPLES.values())
			total += sample.script;
		return total / 1_000_000d;
	}

	public static double totalRenderMillis() {
		double total = 0d;
		for (Sample sample : SAMPLES.values())
			total += sample.render;
		return total / 1_000_000d;
	}

	public static void forget(UUID owner) {
		SAMPLES.remove(owner);
	}

	public static void clear() {
		SAMPLES.clear();
	}

	public record Row(UUID owner, double script, double render) {
		public double total() {
			return script + render;
		}
	}

	private static final class Sample {
		private long pendingScript;
		private long pendingRender;
		private double script;
		private double render;

		void roll() {
			script = script * 0.9d + pendingScript * 0.1d;
			render = render * 0.9d + pendingRender * 0.1d;
			pendingScript = 0L;
			pendingRender = 0L;
		}
	}
}
