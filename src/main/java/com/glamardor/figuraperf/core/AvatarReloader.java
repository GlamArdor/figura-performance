package com.glamardor.figuraperf.core;

import com.glamardor.figuraperf.mixin.AvatarManagerAccessor;
import org.figuramc.figura.avatar.AvatarManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The "reload avatar" from Figura's popup menu, for everyone at once or for one player.
 *
 * <p>Not {@code AvatarManager.clearAllAvatars}: that one also throws away an avatar loaded from the
 * wardrobe and puts the uploaded one in its place. Reloading player by player keeps it, the same way
 * the popup menu does for your own avatar.
 */
public final class AvatarReloader {

	private AvatarReloader() {
	}

	/** Returns how many avatars went off to be downloaded again. */
	public static int reloadAll() {
		// A copy: every reload takes its player out of the very map being walked.
		List<UUID> ids = new ArrayList<>(AvatarManagerAccessor.figuraperf$loadedUsers().keySet());
		for (UUID id : ids)
			AvatarManager.reloadAvatar(id);
		return ids.size();
	}

	public static void reload(UUID id) {
		AvatarManager.reloadAvatar(id);
	}
}
