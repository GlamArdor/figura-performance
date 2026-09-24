package com.glamardor.figuraperf.mixin;

import com.glamardor.figuraperf.config.PerfConfig;
import com.glamardor.figuraperf.gui.PerfHud;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts the same reading behind F3.
 *
 * <p>The debug screen is where anyone already goes looking for numbers, and it costs nothing to
 * keep the panel there: it is only built while the screen is open.
 *
 * <p>The returned list is replaced rather than added to – vanilla is free to hand back something
 * immutable, and a settings panel is not worth a crash.
 */
@Mixin(DebugHud.class)
public abstract class DebugHudMixin {

	@Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true)
	private void figuraperf$addOwnLines(CallbackInfoReturnable<List<String>> cir) {
		if (!PerfConfig.get().debugScreen)
			return;

		List<String> lines = new ArrayList<>(cir.getReturnValue());
		lines.add("");
		lines.addAll(PerfHud.lines());
		cir.setReturnValue(lines);
	}
}
