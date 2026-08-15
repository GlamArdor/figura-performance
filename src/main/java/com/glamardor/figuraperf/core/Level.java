package com.glamardor.figuraperf.core;

/** How much of an avatar is worth spending time on this frame. */
public enum Level {
	/** Nothing at all: no model, no scripts, the player falls back to their vanilla skin. */
	OFF,
	/** The model is drawn, but scripts and animations are thinned out or stopped. */
	MODEL,
	/** Everything Figura would normally do. */
	FULL
}
