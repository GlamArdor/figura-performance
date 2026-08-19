package com.glamardor.figuraperf.mixin;

import com.glamardor.figuraperf.core.AvatarBudget;
import com.glamardor.figuraperf.core.CostMeter;
import com.glamardor.figuraperf.core.Level;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.figuramc.figura.avatar.Avatar;
import org.luaj.vm2.Varargs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The script side of an avatar: every Lua event Figura fires goes through one method, so this is
 * the single place where a cut down avatar can be made to skip its scripts.
 *
 * <p>Skipping the events rather than the methods that fire them matters. Those methods do Figura's
 * own bookkeeping around the call – {@code render} resets the complexity counter for the frame,
 * {@code tick} refreshes permissions and the sound and particle allowances – and cancelling them
 * wholesale leaves that bookkeeping undone. Cancelling only the Lua run leaves Figura's accounting
 * exactly as it found it.
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

	@WrapMethod(method = "run")
	private Varargs figuraperf$run(Object toRun, Avatar.Instructions limit, Object[] args,
			Operation<Varargs> original) {
		Avatar self = figuraperf$self();

		if (figuraperf$skip(self, toRun)) {
			// Same answer Figura gives when a script cannot run at all, so every caller already
			// knows what to do with it.
			return null;
		}

		long start = System.nanoTime();
		Varargs result = original.call(toRun, limit, args);
		CostMeter.recordScript(self.owner, System.nanoTime() - start);
		return result;
	}

	@Unique
	private boolean figuraperf$skip(Avatar self, Object toRun) {
		Level level = AvatarBudget.levelFor(self);
		if (level == Level.FULL)
			return false;
		if (level == Level.OFF)
			return true;

		// Cut down, so the events that fire every frame go, and the ones that fire every tick are
		// thinned out. Anything else – pings, input, chat, sounds – is rare and stays.
		if (!(toRun instanceof String event))
			return false;

		return switch (event) {
			case "RENDER", "POST_RENDER", "WORLD_RENDER", "POST_WORLD_RENDER" -> true;
			case "TICK", "WORLD_TICK" -> self.owner == null || !AvatarBudget.scriptTurn(self.owner);
			default -> false;
		};
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
