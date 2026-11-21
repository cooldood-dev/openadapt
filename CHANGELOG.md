# Changelog

All notable changes to **Adapt+** are documented here.

## [3.1] — 2026-11-21

### Added
- **MediaPlayer HUD** (Modern only) — a now-playing card driven by the Windows media
  session (Spotify, browsers, any player). A hidden PowerShell helper streams the track,
  cover art is decoded into a texture, and **synced lyrics** are pulled from lrclib.net.
  - Multi-line karaoke view: the active line bright, upcoming lines dimmed; falls back to
    a **plain card** when a track has no synced lyrics.
- **Session Info HUD** — time played, kills, and wins, with separate Modern and OldSchool
  layouts.
  - Uses **Lucide icons** and shows the **server favicon** beside the address.
  - Displays the **advertised server name** when joining through a proxy, instead of the
    proxy address.
- **Full autoblock mode** in the Block module (Always / Legit / **Full**). Built against the
  Grim 2.0 source: releases the block before every attack so it passes `MultiActionsA`, and
  drives real input so it stays prediction-consistent.
- **NoSlow: Hypixel mode** and **InvMove: Legit mode** (adapted from openadjust for 26.3).
- **UI Mode** setting in the Theme module — **Modern** and **OldSchool** (BETA).
- **Notification size** editor — scales the notification card (50–150%).

### Changed
- **OldSchool theme** rebuilt as a flat, minimal "Focus"-style look (not just effects off).
  - Watermark matches Moonlight **Novo 2**; Target HUD matches Moonlight **Novo 3**.
- **NoSlow (Hypixel)** reworked to be combat-aware: dropped the per-tick slot-swap (useless
  on 26.3 shields and kick-prone) for a release/reuse method coordinated with autoblock and
  killaura.
- **InvMove (Legit)** and **Hypixel NoSlow** coordinated through SlotHelper / InvManager to
  stop the kicks caused by fighting the shared slot and container state.

### Fixed
- **Full autoblock** no longer holds the shield up permanently — it engages only while a
  target is within hit range, dropping for each swing.
- **Add account** button subtitle ("Microsoft, token or refresh") ellipsizes so it no longer
  overflows the tile.

### Removed
- **TabGUI** and **Armor Status** hidden in OldSchool mode.

### Performance
- Audited the Java render stack (Sodium, Lithium, EntityCulling, render distance, JVM flags).
  Finding: the frame rate was capped by `maxFps`, with the integrated GPU at 2880×1800 as the
  real ceiling — not a mod/render bottleneck.
