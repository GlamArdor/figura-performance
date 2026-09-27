package com.glamardor.figuraperf.gui;

import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;

/**
 * A row in the Cloth list that does something instead of holding a value. Cloth has no builder for
 * that, so this is its boolean toggle with the value taken out.
 */
final class ClothButtonEntry extends TooltipListEntry<Object> {

	private static final int BUTTON_WIDTH = 150;

	private final ButtonWidget button;

	ClothButtonEntry(Text name, Text label, Text tooltip, Runnable action) {
		super(name, null);
		this.button = ButtonWidget.builder(label, b -> action.run()).dimensions(0, 0, BUTTON_WIDTH, 20).build();
		this.button.setTooltip(Tooltip.of(tooltip));
	}

	@Override
	public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
			int mouseX, int mouseY, boolean hovered, float delta) {
		super.render(context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta);
		MinecraftClient client = MinecraftClient.getInstance();
		context.drawTextWithShadow(client.textRenderer, getDisplayedFieldName(), x, y + 6, getPreferredTextColor());
		button.setX(x + entryWidth - BUTTON_WIDTH);
		button.setY(y);
		button.render(context, mouseX, mouseY, delta);
	}

	@Override
	public Object getValue() {
		return null;
	}

	@Override
	public Optional<Object> getDefaultValue() {
		return Optional.empty();
	}

	@Override
	public void save() {
	}

	@Override
	public boolean isEdited() {
		return false;
	}

	@Override
	public List<? extends Element> children() {
		return List.of(button);
	}

	@Override
	public List<? extends Selectable> narratables() {
		return List.of(button);
	}
}
