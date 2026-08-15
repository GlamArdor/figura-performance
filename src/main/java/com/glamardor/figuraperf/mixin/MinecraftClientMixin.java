package com.glamardor.figuraperf.mixin;

import com.glamardor.figuraperf.core.AvatarBudget;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the frame boundary: the budget counts frames, not ticks. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

	@Inject(method = "render(Z)V", at = @At("RETURN"))
	private void figuraperf$endFrame(boolean tick, CallbackInfo ci) {
		AvatarBudget.onFrame();
	}
}
