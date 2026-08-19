package com.glamardor.figuraperf.mixin;

import com.glamardor.figuraperf.core.AvatarBudget;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a switched off avatar invisible to the rest of Figura, the way its panic button does.
 *
 * <p>Cancelling the model build alone turned out to save almost nothing: by then Figura has already
 * saved and edited the vanilla model for that player, walked its own layers for the head, elytra,
 * held items and cape, and drawn its custom nameplate – all of it hanging off this one lookup. When
 * it answers "no avatar", none of that runs at all, which is exactly why panic is so much faster
 * than anything the renderer alone could do.
 */
@Mixin(value = AvatarManager.class, remap = false)
public abstract class AvatarManagerMixin {

	@Inject(method = "getAvatar", at = @At("RETURN"), cancellable = true)
	private static void figuraperf$hideSwitchedOff(CallbackInfoReturnable<Avatar> cir) {
		Avatar avatar = cir.getReturnValue();

		if (avatar != null && AvatarBudget.isHidden(avatar))
			cir.setReturnValue(null);
	}
}
