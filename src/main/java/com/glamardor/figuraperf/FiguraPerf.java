package com.glamardor.figuraperf;

import com.glamardor.figuraperf.config.PerfConfig;
import com.glamardor.figuraperf.core.AvatarBudget;
import com.glamardor.figuraperf.core.CostMeter;
import com.glamardor.figuraperf.gui.LivePreview;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FiguraPerf implements ClientModInitializer {

	public static final String MOD_ID = "figuraperf";
	public static final Logger LOGGER = LoggerFactory.getLogger("Figura Performance");

	private static KeyBinding toggleKey;
	private static KeyBinding overlayKey;
	private static KeyBinding blockKey;

	@Override
	public void onInitializeClient() {
		PerfConfig.get();

		toggleKey = register("toggle", GLFW.GLFW_KEY_UNKNOWN);
		overlayKey = register("overlay", GLFW.GLFW_KEY_UNKNOWN);
		blockKey = register("block", GLFW.GLFW_KEY_UNKNOWN);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			handleKeys(client);
			LivePreview.tick(client);
			AvatarBudget.onTick(client);
		});
	}

	private static KeyBinding register(String name, int key) {
		return KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.figuraperf." + name, InputUtil.Type.KEYSYM, key, "key.categories.figuraperf"));
	}

	private static void handleKeys(MinecraftClient client) {
		PerfConfig config = PerfConfig.get();

		while (toggleKey.wasPressed()) {
			config.enabled = !config.enabled;
			config.save();
			if (!config.enabled)
				CostMeter.clear();
			say(client, Text.translatable(config.enabled
					? "figuraperf.message.enabled"
					: "figuraperf.message.disabled"));
		}

		while (overlayKey.wasPressed()) {
			config.overlay = !config.overlay;
			config.save();
		}

		while (blockKey.wasPressed())
			toggleBlockOfLookedAtPlayer(client, config);
	}

	/**
	 * Blocks or unblocks whoever the player is looking at. Not using the crosshair target: that only
	 * reaches a few blocks, and the avatar worth turning off is usually the one across the square.
	 */
	private static void toggleBlockOfLookedAtPlayer(MinecraftClient client, PerfConfig config) {
		if (client.world == null || client.player == null)
			return;

		Vec3d eyes = client.player.getEyePos();
		Vec3d look = client.player.getRotationVec(1f);

		AbstractClientPlayerEntity best = null;
		double bestScore = 0.985d; // roughly ten degrees off centre

		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			if (player == client.player)
				continue;

			Vec3d toPlayer = player.getPos().add(0, player.getHeight() * 0.5, 0).subtract(eyes);
			double distance = toPlayer.length();
			if (distance < 0.01 || distance > 96)
				continue;

			double score = look.dotProduct(toPlayer.multiply(1 / distance));
			if (score > bestScore) {
				bestScore = score;
				best = player;
			}
		}

		if (best == null) {
			say(client, Text.translatable("figuraperf.message.nobody").formatted(Formatting.GRAY));
			return;
		}

		String name = best.getName().getString();
		boolean removed = config.blocked.removeIf(entry -> entry.equalsIgnoreCase(name));
		if (!removed)
			config.blocked.add(name);
		config.save();

		say(client, Text.translatable(removed
				? "figuraperf.message.unblocked"
				: "figuraperf.message.blocked", name));
	}

	private static void say(MinecraftClient client, Text text) {
		if (client.player != null)
			client.player.sendMessage(text, true);
	}
}
