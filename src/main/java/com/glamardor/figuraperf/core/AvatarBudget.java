package com.glamardor.figuraperf.core;

import com.glamardor.figuraperf.config.PerfConfig;
import com.glamardor.figuraperf.config.Profile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
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
 * Decides, once per tick, how much work each avatar around the player is worth.
 *
 * <p>Figura itself makes no such decision: it ticks the scripts of every loaded avatar, applies
 * every animation and walks every model tree, every frame, no matter where that player is or
 * whether they are on screen at all. Everything here exists to put a budget on that.
 */
public final class AvatarBudget {

	private static final Map<UUID, Level> LEVELS = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> DISTANCES = new ConcurrentHashMap<>();
	private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();
	/** Blocked by the mod itself for being too expensive; forgotten when the game restarts. */
	private static final Set<UUID> AUTO_BLOCKED = ConcurrentHashMap.newKeySet();

	/** Frame counter, used to thin out work that does not have to happen every frame. */
	private static int frame;

	// adaptive state
	private static int fullBudget = Integer.MAX_VALUE;
	private static int framesThisSecond;
	private static long secondStartedAt = System.nanoTime();
	private static int fps = 60;

	// profile state
	private static boolean crowded;
	private static int crowdTicks;

	// what the overlay shows
	private static int countFull;
	private static int countModel;
	private static int countOff;

	private AvatarBudget() {
	}

	// -- called from the client loop --

	/** How many avatar textures have gone to the GPU on this frame. */
	private static int textureUploads;

	/** @return true when this texture may be uploaded now, false when it should wait a frame. */
	public static boolean tryTextureUpload() {
		int limit = PerfConfig.get().textureUploadsPerFrame;
		if (limit <= 0)
			return true;
		return textureUploads++ < limit;
	}

	public static void onFrame() {
		frame++;
		textureUploads = 0;
		CostMeter.endFrame();

		framesThisSecond++;
		long now = System.nanoTime();
		if (now - secondStartedAt >= 1_000_000_000L) {
			// Taken from the game rather than counted here, so the number matches the one F3 shows.
			MinecraftClient client = MinecraftClient.getInstance();
			fps = client == null ? framesThisSecond : client.getCurrentFps();
			framesThisSecond = 0;
			secondStartedAt = now;
			adapt();
		}
	}

	/**
	 * Adaptive mode: give back full avatars while there is headroom, take them away while there is
	 * not. One step per second, so the picture does not flicker between states.
	 */
	private static void adapt() {
		PerfConfig config = PerfConfig.get();
		int ceiling = config.activeProfile().maxFullAvatars;

		if (!config.adaptive) {
			fullBudget = ceiling;
			return;
		}

		if (fullBudget > ceiling)
			fullBudget = ceiling;

		if (fps < config.targetFps * 0.95f)
			fullBudget = Math.max(config.adaptiveFloor, fullBudget - 1);
		else if (fps > config.targetFps * 1.15f)
			fullBudget = Math.min(ceiling, fullBudget + 1);
	}

	public static void onTick(MinecraftClient client) {
		PerfConfig config = PerfConfig.get();
		ClientWorld world = client.world;

		if (!config.enabled || world == null) {
			LEVELS.clear();
			DISTANCES.clear();
			countFull = countModel = countOff = 0;
			return;
		}

		Camera camera = client.gameRenderer.getCamera();
		Vec3d cameraPos = camera.getPos();
		Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
		UUID self = client.player == null ? null : client.player.getUuid();

		Map<UUID, AbstractClientPlayerEntity> players = new HashMap<>();
		for (AbstractClientPlayerEntity player : world.getPlayers())
			players.put(player.getUuid(), player);

		updateCrowdState(config, client, players.values());

		List<Row> rows = new ArrayList<>();
		for (Avatar avatar : AvatarManager.getLoadedAvatars()) {
			UUID owner = avatar.owner;
			if (owner == null)
				continue;

			Entity entity = players.get(owner);
			if (entity == null) {
				// Not a player in sight – an entity avatar, or someone who just left. Leave it alone.
				LEVELS.remove(owner);
				DISTANCES.remove(owner);
				continue;
			}

			NAMES.put(owner, entity.getName().getString());

			Vec3d toEntity = entity.getPos().add(0, entity.getHeight() * 0.5, 0).subtract(cameraPos);
			double distance = toEntity.length();
			// A generous cone: anything roughly in front counts as on screen, so avatars do not
			// pop as the player turns. Very close ones always count, they can be behind the camera.
			boolean onScreen = distance < 6 || (distance > 0 && look.dotProduct(toEntity.multiply(1 / distance)) > 0.1);

			rows.add(new Row(owner, distance, onScreen, owner.equals(self), entity.getName().getString()));
			DISTANCES.put(owner, (float) distance);
		}

		rows.sort(Comparator.comparingDouble(row -> row.distance));

		Profile profile = config.activeProfile();
		int rank = 0;
		int full = 0;
		int model = 0;
		int off = 0;
		int budget = config.adaptive ? Math.min(fullBudget, profile.maxFullAvatars) : profile.maxFullAvatars;

		for (Row row : rows) {
			autoBlockIfExpensive(config, client, row);

			Level level = decide(config, profile, row, rank, budget);
			LEVELS.put(row.owner, level);

			switch (level) {
				case FULL -> full++;
				case MODEL -> model++;
				case OFF -> off++;
			}

			if (level != Level.OFF)
				rank++;
			else
				CostMeter.forget(row.owner);
		}

		countFull = full;
		countModel = model;
		countOff = off;
	}

	/**
	 * Switches between the two profiles by how many people are actually around, with a hold on the
	 * change so that one person walking past a doorway does not flip everything back and forth.
	 */
	private static void updateCrowdState(PerfConfig config, MinecraftClient client,
			Iterable<AbstractClientPlayerEntity> players) {
		if (client.player == null)
			return;

		Vec3d here = client.player.getPos();
		double radius = config.crowdRadius * config.crowdRadius;

		int nearby = 0;
		for (AbstractClientPlayerEntity player : players) {
			if (player != client.player && player.getPos().squaredDistanceTo(here) <= radius)
				nearby++;
		}

		boolean wantsCrowd = crowded
				? nearby >= config.crowdPlayers - 2   // hysteresis: leave later than you enter
				: nearby >= config.crowdPlayers;

		if (wantsCrowd == crowded) {
			crowdTicks = 0;
			return;
		}

		if (++crowdTicks >= 40) { // two seconds of agreeing before the switch
			crowded = wantsCrowd;
			crowdTicks = 0;
		}
	}

	/** Turns off an avatar that is costing more than the player agreed to spend on any one of them. */
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

	private static Level decide(PerfConfig config, Profile profile, Row row, int rank, int budget) {
		if (row.self)
			return config.alwaysFullSelf ? Level.FULL : Level.MODEL;

		if (isListed(config.whitelist, row.name))
			return Level.FULL;

		if (isListed(config.blocked, row.name) || AUTO_BLOCKED.contains(row.owner))
			return Level.OFF;

		if (row.distance > profile.modelDistance || rank >= profile.maxModelAvatars)
			return Level.OFF;

		if (row.distance > profile.fullDistance || rank >= budget)
			return Level.MODEL;

		if (!row.onScreen && config.skipOffscreen)
			return Level.MODEL;

		return Level.FULL;
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

	/** The level of a player by id, for the overlay, which knows owners rather than avatars. */
	public static Level levelOf(UUID owner) {
		Level level = LEVELS.get(owner);
		return level == null ? Level.FULL : level;
	}

	public static Level levelFor(Avatar avatar) {
		if (avatar == null || avatar.owner == null || !PerfConfig.get().enabled)
			return Level.FULL;

		Level level = LEVELS.get(avatar.owner);
		return level == null ? Level.FULL : level;
	}

	/**
	 * Soft level of detail: the share of its complexity budget a distant avatar is allowed to keep.
	 * Figura trims itself once the budget runs out, so the avatar loses parts instead of vanishing.
	 */
	public static float complexityFactor(Avatar avatar) {
		PerfConfig config = PerfConfig.get();
		Profile profile = config.activeProfile();

		if (!config.enabled || !profile.complexityLod || avatar == null || avatar.owner == null)
			return 1f;

		// Anyone still counted as full keeps every part: the whitelist and your own avatar promise
		// exactly that, and trimming them here would quietly break the promise.
		if (levelFor(avatar) == Level.FULL)
			return 1f;

		Float distance = DISTANCES.get(avatar.owner);
		if (distance == null || distance <= profile.fullDistance)
			return 1f;

		float span = Math.max(1f, profile.modelDistance - profile.fullDistance);
		float over = MathHelper.clamp((distance - profile.fullDistance) / span, 0f, 1f);
		return MathHelper.lerp(over, 1f, MathHelper.clamp(profile.complexityFloor, 0.05f, 1f));
	}

	/** True when a cut down avatar is allowed to run its animations on this frame. */
	public static boolean animationTurn() {
		int interval = Math.max(1, PerfConfig.get().activeProfile().animationInterval);
		return frame % interval == 0;
	}

	/** True when a cut down avatar is allowed to tick its scripts on this tick. */
	public static boolean scriptTurn(UUID owner) {
		int interval = Math.max(1, PerfConfig.get().activeProfile().scriptTickInterval);
		if (interval == 1)
			return true;
		// Spread the ticks by owner, so cut down avatars do not all fire on the same tick.
		return (frame + Math.abs(owner.hashCode())) % interval == 0;
	}

	// -- for the overlay and the config --

	public static boolean crowded() {
		return crowded;
	}

	public static int fps() {
		return fps;
	}

	public static int fullCount() {
		return countFull;
	}

	public static int modelCount() {
		return countModel;
	}

	public static int offCount() {
		return countOff;
	}

	public static int budget() {
		return Math.min(fullBudget, PerfConfig.get().activeProfile().maxFullAvatars);
	}

	public static int autoBlockedCount() {
		return AUTO_BLOCKED.size();
	}

	public static void clearAutoBlocked() {
		AUTO_BLOCKED.clear();
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
