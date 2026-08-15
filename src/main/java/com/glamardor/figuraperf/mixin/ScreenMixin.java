package com.glamardor.figuraperf.mixin;

import com.glamardor.figuraperf.gui.ConfigScreenFactory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves the world visible behind our own settings screen.
 *
 * <p>Every setting on that screen is about the avatars standing in front of you, so blurring and
 * darkening them hides the thing being adjusted. Only while our screen is the one on top: every
 * other screen in the game keeps its background exactly as it was.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

	/*
	 * Both injections are require = 0 on purpose. This is decoration: if a future version of the
	 * game renames either method, the mixin quietly does nothing and the background goes back to
	 * being blurred. Nothing that only affects how a menu looks should stop the game from starting.
	 */

	@Inject(method = "applyBlur", at = @At("HEAD"), cancellable = true, require = 0)
	private void figuraperf$noBlur(DrawContext context, CallbackInfo ci) {
		if (figuraperf$isOurs())
			ci.cancel();
	}

	@Inject(method = "renderDarkening(Lnet/minecraft/client/gui/DrawContext;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void figuraperf$noDarkening(DrawContext context, CallbackInfo ci) {
		if (figuraperf$isOurs())
			ci.cancel();
	}

	private static boolean figuraperf$isOurs() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client != null && ConfigScreenFactory.isOurs(client.currentScreen);
	}
}
