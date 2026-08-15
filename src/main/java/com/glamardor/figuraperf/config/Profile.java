package com.glamardor.figuraperf.config;

/**
 * One set of limits. Two of these exist: what to do when you are on your own, and what to do when
 * you are standing in a crowd – the difference between the two is the whole point, since alone
 * there is nothing to save and in a crowd there is everything.
 */
public class Profile {

	/** Past this an avatar keeps its model but loses scripts and animations. */
	public float fullDistance;
	/** Past this the avatar is not drawn at all. */
	public float modelDistance;

	public int maxFullAvatars;
	public int maxModelAvatars;

	/** Cut down avatars animate every Nth frame and tick their scripts every Nth tick. */
	public int animationInterval;
	public int scriptTickInterval;

	/**
	 * Soft level of detail: instead of dropping a distant avatar outright, hand Figura a smaller
	 * complexity budget and let it trim itself.
	 */
	public boolean complexityLod;
	/** How much of the budget is left at the far edge, as a fraction. */
	public float complexityFloor;

	public static Profile solo() {
		Profile profile = new Profile();
		profile.fullDistance = 48f;
		profile.modelDistance = 128f;
		profile.maxFullAvatars = 24;
		profile.maxModelAvatars = 48;
		profile.animationInterval = 1;
		profile.scriptTickInterval = 1;
		profile.complexityLod = false;
		profile.complexityFloor = 0.5f;
		return profile;
	}

	public static Profile crowd() {
		Profile profile = new Profile();
		profile.fullDistance = 20f;
		profile.modelDistance = 64f;
		profile.maxFullAvatars = 6;
		profile.maxModelAvatars = 20;
		profile.animationInterval = 3;
		profile.scriptTickInterval = 4;
		profile.complexityLod = true;
		profile.complexityFloor = 0.35f;
		return profile;
	}

	public void copyFrom(Profile other) {
		fullDistance = other.fullDistance;
		modelDistance = other.modelDistance;
		maxFullAvatars = other.maxFullAvatars;
		maxModelAvatars = other.maxModelAvatars;
		animationInterval = other.animationInterval;
		scriptTickInterval = other.scriptTickInterval;
		complexityLod = other.complexityLod;
		complexityFloor = other.complexityFloor;
	}
}
