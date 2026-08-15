package com.glamardor.figuraperf.mixin;

import com.glamardor.figuraperf.config.PerfConfig;
import com.glamardor.figuraperf.core.AvatarBudget;
import com.glamardor.figuraperf.core.CostMeter;
import com.glamardor.figuraperf.core.Level;
import com.glamardor.figuraperf.core.ShadowPass;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.ImmediateAvatarRenderer;
import org.figuramc.figura.utils.ui.UIHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * The model itself. Every path Figura has – the body, the world parts, the head, elytra, held items,
 * the cape – ends up in {@code commonRender}, so one wrapper covers the whole avatar, and the time
 * spent here is the geometry half of the meter.
 *
 * <p>Figura walks the entire part tree and rebuilds its vertex buffer here, from scratch, every
 * frame, for every avatar, with no distance or visibility test of any kind.
 */
@Mixin(value = ImmediateAvatarRenderer.class, remap = false)
public abstract class ImmediateAvatarRendererMixin {

	@WrapMethod(method = "commonRender")
	private int figuraperf$commonRender(double vertOffset, Operation<Integer> original) {
		PerfConfig config = PerfConfig.get();
		Avatar avatar = ((AvatarRendererAccessor) this).figuraperf$getAvatar();

		if (config.enabled) {
			if (AvatarBudget.levelFor(avatar) == Level.OFF)
				return 0;
			if (config.skipShadowPass && ShadowPass.active())
				return 0;
			if (config.skipPaperdoll && UIHelper.paperdoll)
				return 0;

			figuraperf$trimComplexity(avatar);
		}

		long start = System.nanoTime();
		int complexity = original.call(vertOffset);
		CostMeter.recordRender(avatar == null ? null : avatar.owner, System.nanoTime() - start);
		return complexity;
	}

	/**
	 * Soft level of detail. Figura already stops drawing parts once an avatar runs out of its
	 * complexity budget, so handing a distant one a smaller budget makes it trim itself instead of
	 * disappearing outright. Not restored afterwards on purpose: Figura sets the budget fresh before
	 * every render pass, and the layers drawn after this one should stay inside the same allowance.
	 */
	@Unique
	private void figuraperf$trimComplexity(Avatar avatar) {
		if (avatar == null || avatar.complexity == null)
			return;

		float factor = AvatarBudget.complexityFactor(avatar);
		if (factor >= 1f)
			return;

		avatar.complexity.remaining = Math.max(1, Math.round(avatar.complexity.remaining * factor));
	}
}
