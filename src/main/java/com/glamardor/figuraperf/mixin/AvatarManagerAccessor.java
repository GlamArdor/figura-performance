package com.glamardor.figuraperf.mixin;

import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.avatar.UserData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/** Everyone Figura has asked the backend about, including those whose download failed. */
@Mixin(value = AvatarManager.class, remap = false)
public interface AvatarManagerAccessor {

	@Accessor("LOADED_USERS")
	static Map<UUID, UserData> figuraperf$loadedUsers() {
		throw new AssertionError();
	}
}
