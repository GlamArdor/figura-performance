package com.glamardor.figuraperf.config;

import net.minecraft.text.Text;

public enum ProfileChoice {
	AUTO,
	SOLO,
	CROWD;

	public Text getDisplayName() {
		return Text.translatable("figuraperf.profile." + name().toLowerCase(java.util.Locale.ROOT));
	}

	public ProfileChoice next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
