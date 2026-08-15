# Figura Performance

A client-side Fabric mod that puts a budget on what Figura does, so a crowd of avatars stops
costing you most of your frame rate.

Nothing is needed on the server side, and other players do not need the mod. It only changes what
**your** client spends time on; how your own avatar looks to everyone else is untouched.

> **Status: still being tested.** It works and it has been checked against a real Figura install,
> but it has not had a long run on a busy server yet. Please report anything odd in the issues.

## Why Figura is expensive in a crowd

Three things stand out in Figura's rendering code, and none of them are about avatars being
complicated:

- **There is no distance or visibility test anywhere.** Not one. A player two hundred blocks away
  behind a wall costs exactly as much as the one dancing in front of you.
- **The model is rebuilt from scratch every frame.** Figura walks the whole part tree, recalculates
  the matrices and refills its vertex buffer, per avatar, per frame. Nothing is cached between
  frames.
- **Much of the per avatar work runs per frame, not per tick.** Blockbench animations are applied
  and cleared inside `Minecraft.runTick`, and the script render events are broadcast to every loaded
  avatar, every frame, wherever that player is.

Figura's own panic button shows the size of it: pressing it in a crowd can take a client from 60
frames to 500. This mod is the same idea applied with a scalpel instead of a switch.

## What it does

**Two profiles.** One set of limits for when you are on your own, another for a crowd, switched
automatically by how many players are actually around you. The switch waits two seconds and leaves
the crowd later than it enters, so a single passer-by cannot flip it back and forth. Either profile
can also be pinned by hand.

**Distance.** Past the first distance an avatar keeps its model but loses scripts and animations;
past the second it is not drawn at all and the player falls back to their vanilla skin.

**Limits.** Only the nearest N avatars get the full treatment, only the nearest M are drawn. This is
the setting that matters when everyone is standing in the same square.

**Off screen.** Avatars behind you keep being ticked and animated by Figura. They stop being. Turn
around and they pick up again within a tick.

**Thinning.** Cut down avatars run their animations every Nth frame and their scripts every Nth
tick, spread by player so they do not all fire on the same one.

**Soft level of detail.** Rather than dropping a distant avatar outright, Figura is handed a smaller
complexity budget and trims itself, so the avatar loses parts instead of vanishing.

**Render passes.** Avatars can be skipped in the shadow pass a shader pack draws, and in the paper
doll in the corner, which is a second full render of your own avatar every frame.

**Texture uploads.** Avatars that repaint textures from a script push them to the GPU on the frame
they change; a crowd of those is a stutter. Uploads are spread over frames instead.

**Adaptive mode.** The mod watches the frame rate, tightens the limits when it falls below the
target and relaxes them once the crowd thins out.

**Automatic blocking.** An avatar costing more than a set number of milliseconds a frame can be
switched off on its own, with a line in chat saying who and how much. Off by default.

**Lists.** A whitelist of players who always keep everything, whatever the distance and the limits
say, and a blocklist of players whose avatars are never drawn. Plus a key to block whoever you are
looking at, on the spot.

**Diagnostics overlay.** Frame rate, the active profile, how many avatars are full, cut down or off,
and a list of the most expensive avatars by name – each with its scripts and its geometry counted
separately, and an estimate of the frames it alone is costing you. That split is the useful part: if
the scripts dominate, thinning ticks is the answer; if the geometry does, only a rewritten renderer
would help.

Your own avatar is never cut down by default.

## Keys

Nothing is bound out of the box. In the controls screen, under Figura Performance:

- turn the mod on or off
- toggle the diagnostics overlay
- block the avatar you are looking at

## Settings

Mod Menu, or the config file at `config/figura-performance.json`. With Cloth Config installed you
get the full screen; without it, a plain built-in one. Everything applies as you change it, and the
world stays visible behind the menu – with a crowd in front of you, that is the preview.

## What it does not do

It does not make Figura's renderer faster. Caching geometry between frames, batching by texture and
moving vertex work off the render thread all live inside Figura and would need a fork. This mod
decides how much work is worth doing, not how quickly it is done.

## Building

The mixins target Figura's own classes, so its jar has to be present to compile against. Modrinth
only carries Figura up to 1.21.4, so fetch the Fabric build for 1.21.8 from the Figura releases,
drop it into `libs/`, and point `figura_jar` in `gradle.properties` at it. Then:

```
./gradlew build
```

The jar lands in `build/libs`. Minecraft 1.21.8, Fabric Loader 0.16 or newer, Java 21, Fabric API
and Figura.

## Licence

LGPL-3.0-or-later.
