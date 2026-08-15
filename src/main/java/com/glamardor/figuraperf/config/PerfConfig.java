package com.glamardor.figuraperf.config;

import com.glamardor.figuraperf.FiguraPerf;
import com.glamardor.figuraperf.core.AvatarBudget;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Every setting of the mod, saved as json next to the other mod configs. */
public class PerfConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static PerfConfig instance;

	/** Left at zero on purpose: Gson only overwrites what the file actually contains, so an old
	 * file without this field has to come back as zero for a migration to notice it. */
	public int configVersion = 0;

	// -- master --

	public boolean enabled = true;
	/** The player's own avatar is never cut down – it is the one avatar they always want to see. */
	public boolean alwaysFullSelf = true;

	// -- profiles --

	public ProfileChoice profile = ProfileChoice.AUTO;
	/** How many players have to be around before the crowd profile takes over. */
	public int crowdPlayers = 6;
	public float crowdRadius = 32f;

	public Profile solo = Profile.solo();
	public Profile crowd = Profile.crowd();

	// -- per frame work, shared by both profiles --

	/** Avatars outside the view are still ticked and animated by Figura; this stops that. */
	public boolean skipOffscreen = true;
	/** Upload at most this many avatar textures per frame, to spread the hitch when a crowd loads. 0 disables the limit. */
	public int textureUploadsPerFrame = 6;

	// -- passes --

	/** Shader packs render the world a second time for shadows; avatars there are rarely worth it. */
	public boolean skipShadowPass = true;
	/** The little avatar doll in the corner is a full extra render of your own avatar every frame. */
	public boolean skipPaperdoll = false;

	// -- adaptive --

	public boolean adaptive = true;
	public int targetFps = 60;
	/** Adaptive mode never cuts below this many full avatars. */
	public int adaptiveFloor = 2;

	// -- automatic blocking --

	/** Off by default: turning someone's avatar off without being asked is a strong thing to do. */
	public boolean autoBlock = false;
	/** An avatar costing more than this many milliseconds a frame gets switched off. */
	public float autoBlockMillis = 3f;
	public boolean autoBlockAnnounce = true;

	// -- diagnostics --

	public boolean overlay = false;
	public int overlayRows = 8;
	/** Pushed down by two lines out of the box, so it clears the frame counter Sodium draws. */
	public int overlayOffsetY = 22;

	/** Names of players whose avatars are always off, for the ones with a truly monstrous avatar. */
	public List<String> blocked = new ArrayList<>();
	/** Names that always keep everything, whatever the distance and the limits say. */
	public List<String> whitelist = new ArrayList<>();

	public static PerfConfig get() {
		if (instance == null)
			instance = load();
		return instance;
	}

	/** The limits in force right now, once the profile has been picked. */
	public Profile activeProfile() {
		return switch (profile) {
			case SOLO -> solo;
			case CROWD -> crowd;
			case AUTO -> AvatarBudget.crowded() ? crowd : solo;
		};
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("figura-performance.json");
	}

	private static PerfConfig load() {
		Path path = path();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				PerfConfig loaded = GSON.fromJson(reader, PerfConfig.class);
				if (loaded != null) {
					loaded.repair();
					return loaded;
				}
			} catch (IOException | RuntimeException e) {
				FiguraPerf.LOGGER.warn("Could not read the config, falling back to defaults.", e);
			}
		}
		return new PerfConfig();
	}

	/** An older or hand edited file may be missing whole objects; fill those back in. */
	private void repair() {
		if (blocked == null)
			blocked = new ArrayList<>();
		if (whitelist == null)
			whitelist = new ArrayList<>();
		if (profile == null)
			profile = ProfileChoice.AUTO;
		if (solo == null)
			solo = Profile.solo();
		if (crowd == null)
			crowd = Profile.crowd();
	}

	public void save() {
		try {
			Path path = path();
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			FiguraPerf.LOGGER.warn("Could not save the config.", e);
		}
	}

	public void resetToDefaults() {
		PerfConfig defaults = new PerfConfig();

		enabled = defaults.enabled;
		alwaysFullSelf = defaults.alwaysFullSelf;

		profile = defaults.profile;
		crowdPlayers = defaults.crowdPlayers;
		crowdRadius = defaults.crowdRadius;
		solo.copyFrom(defaults.solo);
		crowd.copyFrom(defaults.crowd);

		skipOffscreen = defaults.skipOffscreen;
		textureUploadsPerFrame = defaults.textureUploadsPerFrame;
		skipShadowPass = defaults.skipShadowPass;
		skipPaperdoll = defaults.skipPaperdoll;

		adaptive = defaults.adaptive;
		targetFps = defaults.targetFps;
		adaptiveFloor = defaults.adaptiveFloor;

		autoBlock = defaults.autoBlock;
		autoBlockMillis = defaults.autoBlockMillis;
		autoBlockAnnounce = defaults.autoBlockAnnounce;

		overlay = defaults.overlay;
		overlayRows = defaults.overlayRows;
		overlayOffsetY = defaults.overlayOffsetY;

		blocked = new ArrayList<>();
		whitelist = new ArrayList<>();
	}
}
