package com.glamardor.figuraperf.gui;

import com.glamardor.figuraperf.config.PerfConfig;
import com.glamardor.figuraperf.core.AvatarBudget;
import com.glamardor.figuraperf.core.CostMeter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * The diagnostics panel: what the mod is doing right now, and which avatars are the expensive ones,
 * with scripts and geometry counted apart. Figura has no such reading of its own – it can tell you
 * how complex an avatar is, but not how many milliseconds a frame that person is costing you.
 */
public final class PerfHud {

	private static final int MARGIN = 4;
	private static final int LINE = 10;
	/** How many of the hidden are named before the rest become a count. */
	private static final int HIDDEN_NAMES = 4;

	private PerfHud() {
	}

	public static void render(DrawContext context) {
		PerfConfig config = PerfConfig.get();
		if (!config.overlay)
			return;

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.options.hudHidden || client.player == null)
			return;

		int y = MARGIN + Math.max(0, config.overlayOffsetY);
		for (String line : lines()) {
			context.drawTextWithShadow(client.textRenderer, line, MARGIN, y, 0xFFFFFFFF);
			y += LINE;
		}
	}

	/**
	 * The panel as plain lines, colours included as formatting codes.
	 *
	 * <p>Shared with the debug screen: the same reading is worth having behind F3, where it costs
	 * nothing to keep around and appears exactly when someone goes looking for numbers.
	 */
	public static List<String> lines() {
		PerfConfig config = PerfConfig.get();
		List<String> lines = new ArrayList<>();

		// Red when off, because a small grey "(off)" was easy to miss and cost an evening of
		// measurements taken with the mod doing nothing.
		lines.add((config.enabled ? "§b" : "§c") + Text.translatable("figuraperf.hud.title").getString()
				+ (config.enabled ? "" : " " + Text.translatable("figuraperf.hud.off").getString()));

		lines.add("§f" + Text.translatable("figuraperf.hud.fps", AvatarBudget.fps()).getString());

		lines.add("§f" + Text.translatable("figuraperf.hud.counts",
				AvatarBudget.shownCount(), AvatarBudget.hiddenCount()).getString()
				+ "  " + Text.translatable("figuraperf.hud.budget", AvatarBudget.budget()).getString());

		double scripts = CostMeter.totalScriptMillis();
		double render = CostMeter.totalRenderMillis();
		double avatars = scripts + render;
		lines.add("§f" + Text.translatable("figuraperf.hud.total",
				String.format("%.2f", avatars),
				String.format("%.2f", scripts),
				String.format("%.2f", render)).getString());

		lines.add("§e" + costLine(avatars));

		if (AvatarBudget.autoBlockedCount() > 0)
			lines.add("§c" + Text.translatable("figuraperf.hud.autoblocked", AvatarBudget.autoBlockedCount()).getString());

		// Who exactly is missing, so a hidden avatar is never a mystery.
		List<String> hidden = AvatarBudget.hiddenNames();
		if (!hidden.isEmpty()) {
			int listed = Math.min(HIDDEN_NAMES, hidden.size());
			StringBuilder names = new StringBuilder();
			for (int i = 0; i < listed; i++) {
				if (i > 0)
					names.append(", ");
				names.append(hidden.get(i));
			}
			if (hidden.size() > listed)
				names.append(" +").append(hidden.size() - listed);

			lines.add("§7" + Text.translatable("figuraperf.hud.hidden_names", names.toString()).getString());
		}

		lines.add("§7" + Text.translatable("figuraperf.hud.header").getString());

		for (CostMeter.Row row : CostMeter.top(Math.max(1, config.overlayRows))) {
			if (row.total() < 0.01)
				continue;

			lines.add(colourFor(row.total()) + String.format("%5.2f  %4.2f/%4.2f  %4d  %s",
					row.total(), row.script(), row.render(), fpsCostOf(row.total()),
					AvatarBudget.nameOf(row.owner())));
		}

		return lines;
	}

	/**
	 * What the avatars are costing, in the only unit anyone actually cares about.
	 *
	 * <p>A frame at the current rate lasts a known number of milliseconds; the avatars take a known
	 * slice out of it. Take that slice away and the rest of the frame would finish sooner, which is
	 * the frame rate the second number reports. It only counts time this mod can see, on the main
	 * thread, and assumes the graphics card is not the thing holding the frame back – with a frame
	 * rate cap or a heavy shader pack the real gain is smaller.
	 */
	private static String costLine(double avatarMillis) {
		int fps = AvatarBudget.fps();
		if (fps <= 0 || avatarMillis <= 0.001)
			return Text.translatable("figuraperf.hud.cost", 0, 0).getString();

		double frameMillis = 1000d / fps;
		int share = (int) Math.round(Math.min(99, avatarMillis / frameMillis * 100));

		double without = Math.max(0.05, frameMillis - avatarMillis);
		int gain = (int) Math.round(1000d / without) - fps;

		return Text.translatable("figuraperf.hud.cost", share, Math.max(0, gain)).getString();
	}

	/** The same arithmetic as the summary line, for one avatar: what it alone is costing in frames. */
	private static int fpsCostOf(double millis) {
		int fps = AvatarBudget.fps();
		if (fps <= 0 || millis <= 0.001)
			return 0;

		double frameMillis = 1000d / fps;
		double without = Math.max(0.05, frameMillis - millis);
		return Math.max(0, (int) Math.round(1000d / without) - fps);
	}

	/** Green while an avatar is cheap, amber when it starts to matter, red when it is the problem. */
	private static String colourFor(double millis) {
		if (millis >= 2.0)
			return "§c";
		if (millis >= 0.5)
			return "§6";
		return "§a";
	}
}
