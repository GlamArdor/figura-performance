package com.glamardor.figuraperf.mixin;

import com.glamardor.figuraperf.config.PerfConfig;
import com.glamardor.figuraperf.core.AvatarBudget;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spreads texture uploads over several frames.
 *
 * <p>Avatars that repaint their textures from a script push them to the GPU again on the very frame
 * they change, and a crowd of those turns into a stutter. A texture that misses its turn simply
 * stays dirty and goes up on one of the next frames; the one already on the GPU is drawn meanwhile.
 * Textures that have never been registered are left alone, otherwise the avatar would flash.
 */
@Mixin(value = FiguraTexture.class, remap = false)
public abstract class FiguraTextureMixin {

	@Shadow
	private boolean registered;

	@Shadow
	private boolean dirty;

	@Shadow
	private boolean isClosed;

	@Inject(method = "uploadIfDirty", at = @At("HEAD"), cancellable = true)
	private void figuraperf$throttleUploads(CallbackInfo ci) {
		PerfConfig config = PerfConfig.get();
		if (!config.enabled || config.textureUploadsPerFrame <= 0)
			return;

		if (!registered || !dirty || isClosed)
			return;

		if (!AvatarBudget.tryTextureUpload())
			ci.cancel();
	}
}
