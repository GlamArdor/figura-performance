package com.glamardor.figuraperf.gui;

import com.glamardor.figuraperf.config.PerfConfig;
import com.glamardor.figuraperf.core.CostMeter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The settings screen used when Cloth Config is not installed: one scrolling list with the sections
 * as headers, a search over all of them, and every value written into the config as it is dragged.
 * The world stays visible behind it, since the avatars in front of you are the preview.
 */
public class FallbackConfigScreen extends Screen {

	private static final int ROW_WIDTH = 310;

	@Nullable
	private final Screen parent;
	private final PerfConfig config = PerfConfig.get();

	private TextFieldWidget search;
	private String filter = "";
	private OptionList list;

	public FallbackConfigScreen(@Nullable Screen parent) {
		super(Text.translatable("figuraperf.config.title"));
		this.parent = parent;
	}

	/** In game the world stays visible: the crowd around you is what the limits are about. */
	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		if (this.client != null && this.client.world != null)
			return;
		super.renderBackground(context, mouseX, mouseY, delta);
	}

	@Override
	protected void init() {
		search = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 26, 200, 18,
				Text.translatable("figuraperf.config.search"));
		search.setPlaceholder(Text.translatable("figuraperf.config.search").formatted(Formatting.DARK_GRAY));
		search.setText(filter);
		search.setChangedListener(value -> {
			filter = value;
			list.clear();
			fillList();
		});
		addDrawableChild(search);

		list = new OptionList(this.client, this.width, this.height - 100, 50, 25);
		addDrawableChild(list);
		fillList();

		ButtonWidget reset = ButtonWidget.builder(Text.translatable("figuraperf.config.reset"), button -> {
			config.resetToDefaults();
			config.save();
			CostMeter.clear();
			clearAndInit();
		}).dimensions(this.width / 2 - 154, this.height - 30, 150, 20).build();
		reset.setTooltip(Tooltip.of(Text.translatable("figuraperf.config.reset.tooltip")));
		addDrawableChild(reset);

		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
				.dimensions(this.width / 2 + 4, this.height - 30, 150, 20)
				.build());
	}

	private void fillList() {
		category("general",
				toggle("enabled", () -> config.enabled, value -> config.enabled = value),
				toggle("always_full_self", () -> config.alwaysFullSelf, value -> config.alwaysFullSelf = value),
				blocksSlider("distance", config.distance, 4, 256, value -> config.distance = value),
				intSlider("max_avatars", config.maxAvatars, 0, 64, value -> config.maxAvatars = value));

		category("frames",
				toggle("skip_offscreen", () -> config.skipOffscreen, value -> config.skipOffscreen = value),
				intSlider("texture_uploads", config.textureUploadsPerFrame, 0, 32,
						value -> config.textureUploadsPerFrame = value));

		category("passes",
				toggle("skip_shadow", () -> config.skipShadowPass, value -> config.skipShadowPass = value),
				toggle("skip_paperdoll", () -> config.skipPaperdoll, value -> config.skipPaperdoll = value));

		category("adaptive",
				toggle("adaptive", () -> config.adaptive, value -> config.adaptive = value),
				intSlider("target_fps", config.targetFps, 20, 240, value -> config.targetFps = value),
				intSlider("adaptive_floor", config.adaptiveFloor, 0, 16, value -> config.adaptiveFloor = value));

		category("autoblock",
				toggle("auto_block", () -> config.autoBlock, value -> config.autoBlock = value),
				millisSlider("auto_block_millis", config.autoBlockMillis, value -> config.autoBlockMillis = value),
				toggle("auto_block_announce", () -> config.autoBlockAnnounce,
						value -> config.autoBlockAnnounce = value));

		category("lists",
				textField("whitelist", String.join(", ", config.whitelist),
						value -> config.whitelist = ClothConfigScreens.splitNames(value)),
				textField("blocked_list", String.join(", ", config.blocked),
						value -> config.blocked = ClothConfigScreens.splitNames(value)));

		category("overlay",
				toggle("debug_screen", () -> config.debugScreen, value -> config.debugScreen = value),
				toggle("overlay", () -> config.overlay, value -> config.overlay = value),
				intSlider("overlay_rows", config.overlayRows, 1, 20, value -> config.overlayRows = value),
				intSlider("overlay_offset", config.overlayOffsetY, 0, 200, value -> config.overlayOffsetY = value));
	}

	/** Adds a header and its rows, unless the search has filtered everything under it away. */
	private void category(String name, Option... options) {
		List<Option> matching = new ArrayList<>();
		for (Option option : options) {
			if (matches(option.key))
				matching.add(option);
		}

		if (matching.isEmpty())
			return;

		list.addHeader(Text.translatable("figuraperf.category." + name));
		for (Option option : matching)
			list.addWidget(option.widget.get());
	}

	private boolean matches(String key) {
		if (filter.isBlank())
			return true;

		String needle = filter.toLowerCase(Locale.ROOT);
		String label = Text.translatable("figuraperf.option." + key).getString().toLowerCase(Locale.ROOT);
		return label.contains(needle) || key.contains(needle);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);
		if (!ConfigScreenFactory.isClothPresent()) {
			context.drawCenteredTextWithShadow(this.textRenderer,
					Text.translatable("figuraperf.config.no_cloth"), this.width / 2, this.height - 44, 0xFF9A9A9A);
		}
	}

	@Override
	public void close() {
		config.save();
		MinecraftClient.getInstance().setScreen(parent);
	}

	// -- option builders --

	private Option toggle(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		return cycle(key, () -> getter.get() ? ScreenTexts.ON : ScreenTexts.OFF,
				() -> setter.accept(!getter.get()));
	}

	private Option cycle(String key, Supplier<Text> value, Runnable onClick) {
		return new Option(key, () -> {
			ButtonWidget button = ButtonWidget.builder(label(key, value.get()), b -> {
				onClick.run();
				config.save();
				b.setMessage(label(key, value.get()));
			}).dimensions(0, 0, ROW_WIDTH, 20).build();
			button.setTooltip(Tooltip.of(Text.translatable("figuraperf.option." + key + ".tooltip")));
			return button;
		});
	}

	private Option intSlider(String key, int current, int min, int max, Consumer<Integer> setter) {
		return new Option(key, () -> new OptionSlider(key, current, min, max,
				value -> {
					setter.accept(Math.round(value));
					config.save();
				},
				value -> Text.literal(String.valueOf(Math.round(value)))));
	}

	private Option blocksSlider(String key, float current, float min, float max, Consumer<Float> setter) {
		return new Option(key, () -> new OptionSlider(key, current, min, max,
				value -> {
					setter.accept(value);
					config.save();
				},
				value -> Text.translatable("figuraperf.unit.blocks", Math.round(value))));
	}

	private Option percentSlider(String key, float current, Consumer<Float> setter) {
		return new Option(key, () -> new OptionSlider(key, current, 0.05f, 1f,
				value -> {
					setter.accept(value);
					config.save();
				},
				value -> Text.literal(Math.round(value * 100) + "%")));
	}

	private Option millisSlider(String key, float current, Consumer<Float> setter) {
		return new Option(key, () -> new OptionSlider(key, current, 0.25f, 20f,
				value -> {
					setter.accept(value);
					config.save();
				},
				value -> Text.translatable("figuraperf.unit.millis", String.format("%.2f", value))));
	}

	private Option textField(String key, String current, Consumer<String> setter) {
		return new Option(key, () -> {
			TextFieldWidget field = new TextFieldWidget(this.textRenderer, 0, 0, ROW_WIDTH, 20,
					Text.translatable("figuraperf.option." + key));
			field.setMaxLength(512);
			// An empty list says nothing about how it should be filled in, so show the shape of it.
			field.setPlaceholder(Text.translatable("figuraperf.option.names_example")
					.formatted(Formatting.DARK_GRAY));
			field.setText(current);
			field.setChangedListener(value -> {
				setter.accept(value);
				config.save();
			});
			field.setTooltip(Tooltip.of(Text.translatable("figuraperf.option." + key + ".tooltip")));
			return field;
		});
	}

	private static Text label(String key, Text value) {
		return Text.translatable("figuraperf.option." + key).append(": ").append(value);
	}

	private record Option(String key, Supplier<ClickableWidget> widget) {
	}

	private static class OptionSlider extends SliderWidget {
		private final String key;
		private final float min;
		private final float max;
		private final Consumer<Float> setter;
		private final Function<Float, Text> display;

		OptionSlider(String key, float current, float min, float max, Consumer<Float> setter,
				Function<Float, Text> display) {
			super(0, 0, ROW_WIDTH, 20, Text.empty(),
					MathHelper.clamp((current - min) / (max - min), 0f, 1f));
			this.key = key;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.display = display;
			setTooltip(Tooltip.of(Text.translatable("figuraperf.option." + key + ".tooltip")));
			updateMessage();
		}

		private float currentValue() {
			return (float) (min + (max - min) * this.value);
		}

		@Override
		protected void updateMessage() {
			setMessage(label(key, display.apply(currentValue())));
		}

		@Override
		protected void applyValue() {
			setter.accept(currentValue());
		}
	}

	private static class OptionList extends ElementListWidget<OptionList.Entry> {

		OptionList(MinecraftClient client, int width, int height, int y, int itemHeight) {
			super(client, width, height, y, itemHeight);
		}

		void addWidget(ClickableWidget widget) {
			addEntry(new WidgetEntry(widget));
		}

		void addHeader(Text text) {
			addEntry(new HeaderEntry(text));
		}

		void clear() {
			clearEntries();
		}

		@Override
		public int getRowWidth() {
			return ROW_WIDTH;
		}

		abstract static class Entry extends ElementListWidget.Entry<Entry> {
		}

		static class WidgetEntry extends Entry {
			private final ClickableWidget widget;

			WidgetEntry(ClickableWidget widget) {
				this.widget = widget;
			}

			@Override
			public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
					int mouseX, int mouseY, boolean hovered, float tickDelta) {
				widget.setX(x);
				widget.setY(y);
				widget.setWidth(entryWidth);
				widget.render(context, mouseX, mouseY, tickDelta);
			}

			@Override
			public List<? extends Element> children() {
				return List.of(widget);
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return List.of(widget);
			}
		}

		static class HeaderEntry extends Entry {
			private final Text text;

			HeaderEntry(Text text) {
				this.text = text;
			}

			@Override
			public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
					int mouseX, int mouseY, boolean hovered, float tickDelta) {
				MinecraftClient client = MinecraftClient.getInstance();
				context.drawCenteredTextWithShadow(client.textRenderer, text, x + entryWidth / 2, y + 8, 0xFFE0C070);
			}

			@Override
			public List<? extends Element> children() {
				return List.of();
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return List.of();
			}
		}
	}
}
