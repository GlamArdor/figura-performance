package com.glamardor.figuraperf.gui;

import com.glamardor.figuraperf.config.PerfConfig;
import com.glamardor.figuraperf.config.ProfileChoice;
import com.glamardor.figuraperf.core.AvatarBudget;
import com.glamardor.figuraperf.core.CostMeter;
import com.glamardor.figuraperf.core.Level;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The diagnostics panel: what the budget is doing right now, and which avatars are the expensive
 * ones – with scripts and geometry apart, because that split is what decides whether thinning the
 * work is enough or Figura's renderer itself is the problem.
 */
public final class PerfHud {

	private static final int MARGIN = 4;
	private static final int LINE = 10;

	private PerfHud() {
	}

	public static void render(DrawContext context) {
		PerfConfig config = PerfConfig.get();
		if (!config.overlay)
			return;

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.options.hudHidden || client.player == null)
			return;

		int x = MARGIN;
		// Started below whatever the frame counter of another mod has already drawn up there.
		int y = MARGIN + Math.max(0, config.overlayOffsetY);

		String title = Text.translatable("figuraperf.hud.title").getString();
		if (!config.enabled)
			title += " " + Text.translatable("figuraperf.hud.off").getString();
		line(context, x, y, title, 0xFF7FD4FF);
		y += LINE;

		String profile = (config.profile == ProfileChoice.AUTO
				? (AvatarBudget.crowded() ? ProfileChoice.CROWD : ProfileChoice.SOLO)
				: config.profile).getDisplayName().getString();
		line(context, x, y, Text.translatable("figuraperf.hud.fps", AvatarBudget.fps()).getString()
				+ "  " + Text.translatable("figuraperf.hud.profile", profile).getString(), 0xFFCFCFCF);
		y += LINE;

		line(context, x, y, Text.translatable("figuraperf.hud.counts",
				AvatarBudget.fullCount(), AvatarBudget.modelCount(), AvatarBudget.offCount()).getString()
				+ "  " + Text.translatable("figuraperf.hud.budget", AvatarBudget.budget()).getString(), 0xFFCFCFCF);
		y += LINE;

		double scripts = CostMeter.totalScriptMillis();
		double render = CostMeter.totalRenderMillis();
		double avatars = scripts + render;
		line(context, x, y, Text.translatable("figuraperf.hud.total",
				String.format("%.2f", avatars),
				String.format("%.2f", scripts),
				String.format("%.2f", render)).getString(), 0xFFCFCFCF);
		y += LINE;

		line(context, x, y, costLine(avatars), 0xFFFFD166);
		y += LINE;

		if (AvatarBudget.autoBlockedCount() > 0) {
			line(context, x, y, Text.translatable("figuraperf.hud.autoblocked",
					AvatarBudget.autoBlockedCount()).getString(), 0xFFFFA0A0);
			y += LINE;
		}

		y += 2;
		line(context, x, y, Text.translatable("figuraperf.hud.header").getString(), 0xFF8A8A8A);
		y += LINE;

		List<CostMeter.Row> rows = CostMeter.top(Math.max(1, config.overlayRows));
		for (CostMeter.Row row : rows) {
			if (row.total() < 0.01)
				continue;

			// Said out loud, because these numbers are what the avatar costs after being cut down,
			// not what it would cost left alone.
			String marker = AvatarBudget.levelOf(row.owner()) == Level.FULL
					? ""
					: " " + Text.translatable("figuraperf.hud.level.model").getString();

			String text = String.format("%5.2f  %4.2f/%4.2f  %4d  %s%s",
					row.total(), row.script(), row.render(), fpsCostOf(row.total()),
					AvatarBudget.nameOf(row.owner()), marker);
			line(context, x, y, text, colourFor(row.total()));
			y += LINE;
		}
	}

	/**
	 * What the avatars are costing, in the only unit anyone actually cares about.
	 *
	 * <p>A frame at the current rate lasts a known number of milliseconds; the avatars take a known
	 * slice out of it. Take that slice away and the rest of the frame would finish sooner, which is
	 * the frame rate the second number reports. It is an estimate, and honestly so: it only counts
	 * time this mod can see, on the main thread, and assumes the graphics card is not the thing
	 * holding the frame back. With a frame rate cap or a heavy shader pack the real gain is smaller.
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
	private static int colourFor(double millis) {
		if (millis >= 2.0)
			return 0xFFFF6B6B;
		if (millis >= 0.5)
			return 0xFFFFD166;
		return 0xFF9BE29B;
	}

	private static void line(DrawContext context, int x, int y, String text, int colour) {
		MinecraftClient client = MinecraftClient.getInstance();
		context.drawTextWithShadow(client.textRenderer, text, x, y, colour);
	}
}
