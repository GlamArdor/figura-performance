package com.glamardor.figuraperf.mixin;

import com.glamardor.figuraperf.core.AvatarBudget;
import com.glamardor.figuraperf.core.CostMeter;
import com.glamardor.figuraperf.core.Level;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Everything Figura runs per avatar that is not the model itself: script ticks, the render and
 * post-render script events, and the Blockbench animations. All of it is timed into the script
 * side of the meter, so the overlay can say whether scripts or geometry are the problem.
 *
 * <p>All four of these run for every loaded avatar with no regard for where that player is, and the
 * last three run once per frame rather than once per tick.
 */
@Mixin(value = Avatar.class, remap = false)
public abstract class AvatarMixin {

	/** Animations have to be cleared exactly when they were applied, so remember whether they were. */
	@Unique
	private boolean figuraperf$animationsApplied;

	@Unique
	private Avatar figuraperf$self() {
		return (Avatar) (Object) this;
	}

	@WrapMethod(method = "tick")
	private void figuraperf$tick(Operation<Void> original) {
		Avatar self = figuraperf$self();
		Level level = AvatarBudget.levelFor(self);

		if (level == Level.OFF)
			return;
		if (level == Level.MODEL && self.owner != null && !AvatarBudget.scriptTurn(self.owner))
			return;

		long start = System.nanoTime();
		original.call();
		CostMeter.recordScript(self.owner, System.nanoTime() - start);
	}

	/** The per frame script render event, sent to every avatar from the level renderer. */
	@WrapMethod(method = "render(F)V")
	private void figuraperf$renderEvent(float delta, Operation<Void> original) {
		Avatar self = figuraperf$self();
		if (AvatarBudget.levelFor(self) != Level.FULL)
			return;

		long start = System.nanoTime();
		original.call(delta);
		CostMeter.recordScript(self.owner, System.nanoTime() - start);
	}

	@WrapMethod(method = "postWorldRenderEvent")
	private void figuraperf$postWorldRenderEvent(float delta, Operation<Void> original) {
		Avatar self = figuraperf$self();
		if (AvatarBudget.levelFor(self) != Level.FULL)
			return;

		long start = System.nanoTime();
		original.call(delta);
		CostMeter.recordScript(self.owner, System.nanoTime() - start);
	}

	@WrapMethod(method = "renderEvent")
	private void figuraperf$renderEventWithMatrix(float delta, FiguraMat4 poseMatrix, Operation<Void> original) {
		Avatar self = figuraperf$self();
		if (AvatarBudget.levelFor(self) != Level.FULL)
			return;

		long start = System.nanoTime();
		original.call(delta, poseMatrix);
		CostMeter.recordScript(self.owner, System.nanoTime() - start);
	}

	@WrapMethod(method = "applyAnimations")
	private void figuraperf$applyAnimations(Operation<Void> original) {
		Avatar self = figuraperf$self();
		Level level = AvatarBudget.levelFor(self);
		boolean allowed = level == Level.FULL || (level == Level.MODEL && AvatarBudget.animationTurn());

		figuraperf$animationsApplied = allowed;
		if (!allowed)
			return;

		long start = System.nanoTime();
		original.call();
		CostMeter.recordScript(self.owner, System.nanoTime() - start);
	}

	@Inject(method = "clearAnimations", at = @At("HEAD"), cancellable = true)
	private void figuraperf$clearAnimations(CallbackInfo ci) {
		if (!figuraperf$animationsApplied)
			ci.cancel();
	}
}
