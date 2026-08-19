package com.glamardor.figuraperf.gui;

import com.glamardor.figuraperf.config.PerfConfig;
import com.glamardor.figuraperf.core.AvatarBudget;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Cloth Config version of the settings screen. Only ever touched from inside a try in the factory:
 * Cloth is a compile only dependency and may not be installed at all.
 */
final class ClothConfigScreens {

	/** Sliders are integers in Cloth, so fractions are edited in hundredths. */
	private static final int HUNDREDTHS = 100;

	/** Widgets to read back into the config every tick, so the limits follow the sliders. */
	private static final List<Runnable> LIVE = new ArrayList<>();

	private ClothConfigScreens() {
	}

	static Screen build(@Nullable Screen parent) {
		PerfConfig config = PerfConfig.get();
		PerfConfig defaults = new PerfConfig();
		LIVE.clear();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Text.translatable("figuraperf.config.title"))
				.setSavingRunnable(() -> {
					LivePreview.markSaved();
					config.save();
				});

		// See the crowd while setting the limits on it: a solid menu background would hide the very
		// thing every slider here is about.
		builder.setTransparentBackground(true);
		// One long list with the categories down the side, rather than tabs. Tabbed, the search box
		// only ever looks inside the tab you are standing in.
		builder.setGlobalized(true);
		builder.setGlobalizedExpanded(true);

		ConfigEntryBuilder entries = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(Text.translatable("figuraperf.category.general"));
		general.addEntry(toggle(entries, "enabled", config.enabled, defaults.enabled,
				value -> config.enabled = value));
		general.addEntry(toggle(entries, "always_full_self", config.alwaysFullSelf, defaults.alwaysFullSelf,
				value -> config.alwaysFullSelf = value));

		general.addEntry(blocks(entries, "distance", config.distance, defaults.distance, 4, 256,
				value -> config.distance = value));
		general.addEntry(count(entries, "max_avatars", config.maxAvatars, defaults.maxAvatars, 0, 64,
				value -> config.maxAvatars = value));

		ConfigCategory frames = builder.getOrCreateCategory(Text.translatable("figuraperf.category.frames"));
		frames.addEntry(toggle(entries, "skip_offscreen", config.skipOffscreen, defaults.skipOffscreen,
				value -> config.skipOffscreen = value));
		frames.addEntry(count(entries, "texture_uploads", config.textureUploadsPerFrame,
				defaults.textureUploadsPerFrame, 0, 32, value -> config.textureUploadsPerFrame = value));

		ConfigCategory passes = builder.getOrCreateCategory(Text.translatable("figuraperf.category.passes"));
		passes.addEntry(toggle(entries, "skip_shadow", config.skipShadowPass, defaults.skipShadowPass,
				value -> config.skipShadowPass = value));
		passes.addEntry(toggle(entries, "skip_paperdoll", config.skipPaperdoll, defaults.skipPaperdoll,
				value -> config.skipPaperdoll = value));

		ConfigCategory adaptive = builder.getOrCreateCategory(Text.translatable("figuraperf.category.adaptive"));
		adaptive.addEntry(toggle(entries, "adaptive", config.adaptive, defaults.adaptive,
				value -> config.adaptive = value));
		adaptive.addEntry(count(entries, "target_fps", config.targetFps, defaults.targetFps, 20, 240,
				value -> config.targetFps = value));
		adaptive.addEntry(count(entries, "adaptive_floor", config.adaptiveFloor, defaults.adaptiveFloor, 0, 16,
				value -> config.adaptiveFloor = value));

		ConfigCategory auto = builder.getOrCreateCategory(Text.translatable("figuraperf.category.autoblock"));
		auto.addEntry(toggle(entries, "auto_block", config.autoBlock, defaults.autoBlock,
				value -> config.autoBlock = value));
		auto.addEntry(millis(entries, "auto_block_millis", config.autoBlockMillis, defaults.autoBlockMillis,
				value -> config.autoBlockMillis = value));
		auto.addEntry(toggle(entries, "auto_block_announce", config.autoBlockAnnounce, defaults.autoBlockAnnounce,
				value -> config.autoBlockAnnounce = value));
		auto.addEntry(entries.startTextDescription(Text.translatable("figuraperf.option.auto_block_current",
				String.join(", ", AvatarBudget.autoBlockedNames()))).build());

		ConfigCategory lists = builder.getOrCreateCategory(Text.translatable("figuraperf.category.lists"));
		// An empty field says nothing about how it should be filled in, so spell it out once.
		lists.addEntry(entries.startTextDescription(Text.translatable("figuraperf.option.names_hint",
				Text.translatable("figuraperf.option.names_example"))).build());
		lists.addEntry(names(entries, "whitelist", config.whitelist, value -> config.whitelist = value));
		lists.addEntry(names(entries, "blocked_list", config.blocked, value -> config.blocked = value));

		ConfigCategory overlay = builder.getOrCreateCategory(Text.translatable("figuraperf.category.overlay"));
		overlay.addEntry(toggle(entries, "overlay", config.overlay, defaults.overlay,
				value -> config.overlay = value));
		overlay.addEntry(count(entries, "overlay_rows", config.overlayRows, defaults.overlayRows, 1, 20,
				value -> config.overlayRows = value));
		overlay.addEntry(count(entries, "overlay_offset", config.overlayOffsetY, defaults.overlayOffsetY, 0, 200,
				value -> config.overlayOffsetY = value));

		Screen screen = builder.build();
		LivePreview.start(screen, LIVE);
		return screen;
	}

	// -- entry builders, each one also feeding the live preview --

	private static AbstractConfigListEntry<?> toggle(ConfigEntryBuilder entries, String key, boolean value,
			boolean fallback, Consumer<Boolean> save) {
		var entry = entries.startBooleanToggle(text(key), value)
				.setDefaultValue(fallback)
				.setTooltip(tooltip(key))
				.setSaveConsumer(save)
				.build();
		LIVE.add(() -> save.accept(entry.getValue()));
		return entry;
	}

	private static AbstractConfigListEntry<?> count(ConfigEntryBuilder entries, String key, int value,
			int fallback, int min, int max, Consumer<Integer> save) {
		IntegerSliderEntry entry = entries.startIntSlider(text(key), value, min, max)
				.setDefaultValue(fallback)
				.setTooltip(tooltip(key))
				.setSaveConsumer(save)
				.build();
		LIVE.add(() -> save.accept(entry.getValue()));
		return entry;
	}

	private static AbstractConfigListEntry<?> blocks(ConfigEntryBuilder entries, String key, float value,
			float fallback, int min, int max, Consumer<Float> save) {
		IntegerSliderEntry entry = entries.startIntSlider(text(key), Math.round(value), min, max)
				.setDefaultValue(Math.round(fallback))
				.setTextGetter(v -> Text.translatable("figuraperf.unit.blocks", v))
				.setTooltip(tooltip(key))
				.setSaveConsumer(v -> save.accept((float) v))
				.build();
		LIVE.add(() -> save.accept((float) entry.getValue()));
		return entry;
	}

	private static AbstractConfigListEntry<?> percent(ConfigEntryBuilder entries, String key, float value,
			float fallback, int min, int max, Consumer<Float> save) {
		IntegerSliderEntry entry = entries.startIntSlider(text(key), Math.round(value * HUNDREDTHS), min, max)
				.setDefaultValue(Math.round(fallback * HUNDREDTHS))
				.setTextGetter(v -> Text.literal(v + "%"))
				.setTooltip(tooltip(key))
				.setSaveConsumer(v -> save.accept(v / (float) HUNDREDTHS))
				.build();
		LIVE.add(() -> save.accept(entry.getValue() / (float) HUNDREDTHS));
		return entry;
	}

	/** Milliseconds edited in hundredths, so the slider has a usable resolution. */
	private static AbstractConfigListEntry<?> millis(ConfigEntryBuilder entries, String key, float value,
			float fallback, Consumer<Float> save) {
		IntegerSliderEntry entry = entries.startIntSlider(text(key), Math.round(value * HUNDREDTHS), 25, 2000)
				.setDefaultValue(Math.round(fallback * HUNDREDTHS))
				.setTextGetter(v -> Text.translatable("figuraperf.unit.millis", String.format("%.2f", v / 100.0f)))
				.setTooltip(tooltip(key))
				.setSaveConsumer(v -> save.accept(v / (float) HUNDREDTHS))
				.build();
		LIVE.add(() -> save.accept(entry.getValue() / (float) HUNDREDTHS));
		return entry;
	}

	/**
	 * A plain text field rather than Cloth's string list: there the input hides behind a fold out
	 * arrow and the plus button reads as broken.
	 */
	private static AbstractConfigListEntry<?> names(ConfigEntryBuilder entries, String key, List<String> value,
			Consumer<List<String>> save) {
		var entry = entries.startStrField(text(key), String.join(", ", value))
				.setDefaultValue("")
				.setTooltip(tooltip(key))
				.setSaveConsumer(text -> save.accept(splitNames(text)))
				.build();
		LIVE.add(() -> save.accept(splitNames(entry.getValue())));
		return entry;
	}

	static List<String> splitNames(String value) {
		List<String> names = new ArrayList<>();
		for (String piece : value.split(",")) {
			String name = piece.trim();
			if (!name.isEmpty())
				names.add(name);
		}
		return names;
	}

	private static Text text(String key) {
		return Text.translatable("figuraperf.option." + key);
	}

	private static Text[] tooltip(String key) {
		return new Text[] { Text.translatable("figuraperf.option." + key + ".tooltip") };
	}
}
