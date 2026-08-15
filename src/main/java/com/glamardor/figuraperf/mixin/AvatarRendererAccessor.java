package com.glamardor.figuraperf.mixin;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The renderer knows whose avatar it is, but keeps it to itself. */
@Mixin(value = AvatarRenderer.class, remap = false)
public interface AvatarRendererAccessor {

	@Accessor("avatar")
	Avatar figuraperf$getAvatar();
}
