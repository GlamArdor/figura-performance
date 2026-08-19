package com.glamardor.figuraperf.mixin;

import com.glamardor.figuraperf.core.AvatarBudget;
import com.glamardor.figuraperf.core.CostMeter;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.figuramc.figura.avatar.Avatar;
import org.luaj.vm2.Varargs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * The script side of an avatar. Every Lua event Figura fires goes through one method, which makes
 * this both the cheapest place to time scripts and the only place that has to be touched to stop
 * them running for an avatar nobody can see.
 *
 * <p>Scripts are only ever stopped for a hidden avatar, never thinned for a visible one. Running
 * them every second or third tick was tried: bodies that lean and follow the camera turned jerky,
 * parts went missing, and the frames it saved were lost in the noise.
 *
 * <p>Stopping the events rather than the methods that fire them matters. Those methods carry
 * Figura's own bookkeeping around the call – {@code render} resets the complexity counter for the
 * frame, {@code tick} refreshes permissions and the sound and particle allowances – and cancelling
 * them wholesale leaves that undone.
 */
@Mixin(value = Avatar.class, remap = false)
public abstract class AvatarMixin {

	@Unique
	private Avatar figuraperf$self() {
		return (Avatar) (Object) this;
	}

	@WrapMethod(method = "run")
	private Varargs figuraperf$run(Object toRun, Avatar.Instructions limit, Object[] args,
			Operation<Varargs> original) {
		Avatar self = figuraperf$self();

		if (AvatarBudget.isHidden(self)) {
			// The same answer Figura gives when a script cannot run at all, so every caller already
			// knows what to do with it.
			return null;
		}

		long start = System.nanoTime();
		Varargs result = original.call(toRun, limit, args);
		CostMeter.recordScript(self.owner, System.nanoTime() - start);
		return result;
	}
}
