package com.glamardor.figuraperf.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class FiguraPerfModMenu implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		// Fully qualified: Mod Menu has a ConfigScreenFactory of its own, and so do we.
		return com.glamardor.figuraperf.gui.ConfigScreenFactory::create;
	}
}
