package com.glamardor.figuraperf.mixin;

import net.minecraft.client.MinecraftClient;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.UserData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Throwing old avatars away closes their textures, and that only works on the render thread. Figura
 * does it from wherever the news arrived: the websocket when someone swaps their avatar, the HTTP
 * pool when their user data comes back. Off the render thread the close throws halfway through, the
 * avatar is left with its textures freed, and the next resource reload fails on them with "Image is
 * not allocated".
 *
 * <p>So off the render thread the old avatars are only taken out of the queue here, right away, so
 * whatever Figura loads next goes in clean, and their cleanup is handed to the render thread.
 */
@Mixin(value = UserData.class, remap = false)
public abstract class UserDataMixin {

	@Shadow
	@Final
	private Queue<Avatar> avatars;

	@Inject(method = "clear", at = @At("HEAD"), cancellable = true)
	private void figuraperf$cleanOnRenderThread(CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.isOnThread())
			return;

		List<Avatar> old = new ArrayList<>(avatars);
		avatars.removeAll(old);
		client.execute(() -> old.forEach(Avatar::clean));
		ci.cancel();
	}
}
