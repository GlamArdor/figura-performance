package com.glamardor.figuraperf.mixin;

import com.glamardor.figuraperf.gui.PerfHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the diagnostics panel over the HUD. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

	@Inject(method = "render", at = @At("RETURN"))
	private void figuraperf$overlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		PerfHud.render(context);
	}
}
