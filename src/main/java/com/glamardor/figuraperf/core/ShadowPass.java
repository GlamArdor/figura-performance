package com.glamardor.figuraperf.core;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Tells whether a shader pack is currently drawing the shadow map.
 *
 * <p>Reached by reflection on purpose: Iris is not a dependency of this mod, and players without it
 * should never notice this class exists.
 */
public final class ShadowPass {

	private static boolean checked;
	private static Object irisApi;
	private static Method isShadowPass;

	private ShadowPass() {
	}

	public static boolean active() {
		if (!checked)
			lookUp();

		if (isShadowPass == null)
			return false;

		try {
			return (Boolean) isShadowPass.invoke(irisApi);
		} catch (ReflectiveOperationException | RuntimeException e) {
			isShadowPass = null;
			return false;
		}
	}

	private static void lookUp() {
		checked = true;

		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("iris") && !loader.isModLoaded("oculus"))
			return;

		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			irisApi = api.getMethod("getInstance").invoke(null);
			isShadowPass = api.getMethod("isRenderingShadowPass");
		} catch (ReflectiveOperationException | RuntimeException e) {
			irisApi = null;
			isShadowPass = null;
		}
	}
}
