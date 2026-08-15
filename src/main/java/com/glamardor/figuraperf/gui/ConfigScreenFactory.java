package com.glamardor.figuraperf.gui;

import com.glamardor.figuraperf.FiguraPerf;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Hands out whichever settings screen the player can actually run: the Cloth Config one when that
 * mod is present, our own otherwise.
 */
public final class ConfigScreenFactory {

	@Nullable
	private static Screen ours;

	private ConfigScreenFactory() {
	}

	public static boolean isClothPresent() {
		FabricLoader loader = FabricLoader.getInstance();
		return loader.isModLoaded("cloth-config") || loader.isModLoaded("cloth-config2");
	}

	public static Screen create(@Nullable Screen parent) {
		if (isClothPresent()) {
			try {
				// Arms the live preview itself, since only it knows its own widgets.
				Screen screen = ClothConfigScreens.build(parent);
				ours = screen;
				return screen;
			} catch (Throwable t) {
				// A Cloth major version bump should degrade to our own screen, not crash the game.
				FiguraPerf.LOGGER.warn("Cloth Config screen failed, using the built-in one", t);
			}
		}

		// The built-in screen writes straight into the config as things are dragged, so it needs no
		// preview machinery.
		Screen screen = new FallbackConfigScreen(parent);
		ours = screen;
		return screen;
	}

	/** True while the screen on top is ours, which is when the world behind it stays clear. */
	public static boolean isOurs(@Nullable Screen screen) {
		return screen != null && screen == ours;
	}
}
