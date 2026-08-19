package com.glamardor.figuraperf.mixin;

import com.glamardor.figuraperf.config.PerfConfig;
import com.glamardor.figuraperf.core.CostMeter;
import com.glamardor.figuraperf.core.ShadowPass;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.ImmediateAvatarRenderer;
import org.figuramc.figura.utils.ui.UIHelper;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The model itself. Every path Figura has – the body, the world parts, the head, elytra, held items,
 * the cape – ends up in {@code commonRender}, so one wrapper both times the geometry and covers the
 * two passes worth skipping outright.
 *
 * <p>Hidden avatars never reach this point: they are turned away at the lookup, before Figura does
 * any of the work around the model.
 */
@Mixin(value = ImmediateAvatarRenderer.class, remap = false)
public abstract class ImmediateAvatarRendererMixin {

	@WrapMethod(method = "commonRender")
	private int figuraperf$commonRender(double vertOffset, Operation<Integer> original) {
		PerfConfig config = PerfConfig.get();
		Avatar avatar = ((AvatarRendererAccessor) this).figuraperf$getAvatar();

		if (config.enabled) {
			// With a shader pack the world is drawn a second time for the shadow map; avatars in it
			// are rarely worth the cost. The paper doll is a second full render of your own avatar.
			if (config.skipShadowPass && ShadowPass.active())
				return 0;
			if (config.skipPaperdoll && UIHelper.paperdoll)
				return 0;
		}

		long start = System.nanoTime();
		int complexity = original.call(vertOffset);
		CostMeter.recordRender(avatar == null ? null : avatar.owner, System.nanoTime() - start);
		return complexity;
	}
}
