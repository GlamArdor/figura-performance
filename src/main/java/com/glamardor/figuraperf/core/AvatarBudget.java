package com.glamardor.figuraperf.core;

import com.glamardor.figuraperf.config.PerfConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides, once per tick, whose avatars are worth showing.
 *
 * <p>Only one decision is made, and it is deliberately blunt: an avatar is either shown exactly as
 * Figura would show it, or it is not shown at all. Everything in between was tried and dropped.
 * Trimming an avatar down – skipping its scripts, thinning its animations, cutting its complexity –
 * broke how it looked and saved almost nothing, because the cost of an avatar sits in the work
 * Figura does around it: editing the vanilla model, walking its layers, drawing its nameplate. All
 * of that hangs off a single lookup, and only hiding the avatar outright avoids any of it.
 */
public final class AvatarBudget {

	private static final Set<UUID> HIDDEN = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();
	/** Hidden by the mod itself for being too expensive; forgotten when the game restarts. */
	private static final Set<UUID> AUTO_BLOCKED = ConcurrentHashMap.newKeySet();

	private static int textureUploads;

	// adaptive state
	private static int budget = Integer.MAX_VALUE;
	private static long secondStartedAt = System.nanoTime();
	private static int fps = 60;

	// what the overlay shows
	private static int countShown;
	private static int countHidden;
	/** Names of the hidden ones, nearest first, so the overlay can say who is missing. */
	private static volatile List<String> hiddenNames = List.of();

	private AvatarBudget() {
	}

	// -- called from the client loop --

	/** @return true when this texture may be uploaded now, false when it should wait a frame. */
	public static boolean tryTextureUpload() {
		int limit = PerfConfig.get().textureUploadsPerFrame;
		if (limit <= 0)
			return true;
		return textureUploads++ < limit;
	}

	public static void onFrame() {
		textureUploads = 0;
		CostMeter.endFrame();

		long now = System.nanoTime();
		if (now - secondStartedAt >= 1_000_000_000L) {
			// Taken from the game rather than counted here, so the number matches the one F3 shows.
			MinecraftClient client = MinecraftClient.getInstance();
			if (client != null)
				fps = client.getCurrentFps();
			secondStartedAt = now;
			adapt();
		}
	}

	/**
	 * Adaptive mode: show more avatars while there is headroom, fewer while there is not. One step
	 * per second, so the picture does not flicker between states.
	 */
	private static void adapt() {
		PerfConfig config = PerfConfig.get();
		int ceiling = config.maxAvatars;

		if (!config.adaptive) {
			budget = ceiling;
			return;
		}

		if (budget > ceiling)
			budget = ceiling;

		if (fps < config.targetFps * 0.95f)
			budget = Math.max(config.adaptiveFloor, budget - 1);
		else if (fps > config.targetFps * 1.15f)
			budget = Math.min(ceiling, budget + 1);
	}

	public static void onTick(MinecraftClient client) {
		PerfConfig config = PerfConfig.get();
		ClientWorld world = client.world;

		if (!config.enabled || world == null) {
			HIDDEN.clear();
			hiddenNames = List.of();
			countShown = countHidden = 0;
			return;
		}

		Camera camera = client.gameRenderer.getCamera();
		Vec3d cameraPos = camera.getPos();
		Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
		UUID self = client.player == null ? null : client.player.getUuid();

		Map<UUID, AbstractClientPlayerEntity> players = new HashMap<>();
		for (AbstractClientPlayerEntity player : world.getPlayers())
			players.put(player.getUuid(), player);

		List<Row> rows = new ArrayList<>();
		for (Avatar avatar : AvatarManager.getLoadedAvatars()) {
			UUID owner = avatar.owner;
			if (owner == null)
				continue;

			Entity entity = players.get(owner);
			if (entity == null) {
				// Not a player in sight – an entity avatar, or someone who just left. Leave it alone.
				HIDDEN.remove(owner);
				continue;
			}

			NAMES.put(owner, entity.getName().getString());

			Vec3d toEntity = entity.getPos().add(0, entity.getHeight() * 0.5, 0).subtract(cameraPos);
			double distance = toEntity.length();
			// A generous cone: anything roughly in front counts as on screen, so avatars do not pop
			// as the player turns. Very close ones always count, they can be behind the camera.
			boolean onScreen = distance < 6 || (distance > 0 && look.dotProduct(toEntity.multiply(1 / distance)) > 0.1);

			rows.add(new Row(owner, distance, onScreen, owner.equals(self), entity.getName().getString()));
		}

		rows.sort(Comparator.comparingDouble(row -> row.distance));

		int rank = 0;
		int shown = 0;
		List<String> hiddenNow = new ArrayList<>();
		int allowance = config.adaptive ? Math.min(budget, config.maxAvatars) : config.maxAvatars;

		for (Row row : rows) {
			autoBlockIfExpensive(config, client, row);

			if (hide(config, row, rank, allowance)) {
				HIDDEN.add(row.owner);
				CostMeter.forget(row.owner);
				hiddenNow.add(row.name);
			} else {
				HIDDEN.remove(row.owner);
				shown++;
				rank++;
			}
		}

		countShown = shown;
		countHidden = hiddenNow.size();
		hiddenNames = List.copyOf(hiddenNow);
	}

	/** Hides an avatar that is costing more than the player agreed to spend on any one of them. */
	private static void autoBlockIfExpensive(PerfConfig config, MinecraftClient client, Row row) {
		if (!config.autoBlock || row.self || AUTO_BLOCKED.contains(row.owner))
			return;

		if (isListed(config.whitelist, row.name))
			return;

		if (CostMeter.totalFor(row.owner) < config.autoBlockMillis)
			return;

		AUTO_BLOCKED.add(row.owner);

		if (config.autoBlockAnnounce && client.player != null) {
			client.player.sendMessage(Text.translatable("figuraperf.message.autoblocked",
					row.name, String.format("%.1f", CostMeter.totalFor(row.owner))), false);
		}
	}

	private static boolean hide(PerfConfig config, Row row, int rank, int allowance) {
		if (row.self)
			return !config.alwaysFullSelf;

		if (isListed(config.whitelist, row.name))
			return false;

		if (isListed(config.blocked, row.name) || AUTO_BLOCKED.contains(row.owner))
			return true;

		if (row.distance > config.distance || rank >= allowance)
			return true;

		return !row.onScreen && config.skipOffscreen;
	}

	private static boolean isListed(List<String> names, String name) {
		if (names == null)
			return false;
		for (String listed : names) {
			if (listed.equalsIgnoreCase(name))
				return true;
		}
		return false;
	}

	// -- asked by the mixins, many times per frame, so keep it cheap --

	public static boolean isHidden(Avatar avatar) {
		if (avatar == null || avatar.owner == null || !PerfConfig.get().enabled)
			return false;
		return HIDDEN.contains(avatar.owner);
	}

	// -- for the overlay and the config --

	public static int fps() {
		return fps;
	}

	public static int shownCount() {
		return countShown;
	}

	public static int hiddenCount() {
		return countHidden;
	}

	/** Who is being hidden right now, nearest first. */
	public static List<String> hiddenNames() {
		return hiddenNames;
	}

	public static int budget() {
		return Math.min(budget, PerfConfig.get().maxAvatars);
	}

	public static int autoBlockedCount() {
		return AUTO_BLOCKED.size();
	}

	public static Set<String> autoBlockedNames() {
		Set<String> names = new HashSet<>();
		for (UUID owner : AUTO_BLOCKED)
			names.add(nameOf(owner));
		return names;
	}

	public static String nameOf(UUID owner) {
		return NAMES.getOrDefault(owner, owner.toString().substring(0, 8));
	}

	private record Row(UUID owner, double distance, boolean onScreen, boolean self, String name) {
	}
}
