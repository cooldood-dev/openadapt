<div align="center">

# Adapt+

**A modern Fabric utility client for Minecraft 26.3.**

Custom rendering, a free-camera rotation system, anticheat-aware movement and combat modes, and a glassy configurable ClickGUI — built from scratch for the latest Minecraft.

![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62B47A)
![Fabric](https://img.shields.io/badge/Fabric-0.20.1%2B-DBB969)
![Java](https://img.shields.io/badge/Java-25-E76F00)
![License](https://img.shields.io/badge/License-MIT-blue)
![Version](https://img.shields.io/badge/version-3.1-FF5577)

</div>

---

> [!WARNING]
> **Educational / research use.** Adapt+ is a client modification shared for learning how modern Minecraft rendering, packet handling and anticheat interaction work. Using gameplay-altering mods on multiplayer servers usually breaks their rules and can get your account banned. You are responsible for how and where you run it. Don't use it anywhere you aren't allowed to.

---

## Highlights

- **Custom UI engine** — a glassy ClickGUI with frosted-glass blur, bloom and a nebula background, drawn through a bespoke `Render2D` / `Glass` layer and a cached glyph font renderer (Poppins / Inter / Material Icons).
- **Two UI modes** — a full-effect **Modern** theme and a flat, minimal **OldSchool** theme (BETA), switchable from the Theme module.
- **Free-camera rotation system** — modules aim the *server* rotation while the rendered camera stays where you look, so aim-assisted features don't lock your view.
- **Anticheat-aware modes** — NoSlow, Scaffold, autoblock and more ship dedicated NCP / Hypixel / Watchdog variants rather than one-size-fits-all.
- **Online + local configs** — save named configs locally or pull shared ones from the [`adapt-config`](https://github.com/cooldood-dev/adapt-config) repo straight from the config menu. Your keybinds are preserved across every config load.
- **Bundled performance mods** — ships with Sodium, Lithium, EntityCulling and FerriteCore tuning in mind.

## Modules

**Combat** — KillAura · Block (autoblock: Always / Legit / Full) · Bed Breaker · Velocity · Criticals · Keep Sprint · Reach · Piercing · Auto Potion · Auto Projectile · Backtrack

**Movement** — Movement Fix · Speed · Target Strafe · No Slow (NCP / Hypixel) · Inv Move (Vanilla / Legit) · Flight · Phase · Anti Void · Long Jump · Step · Sprint

**World** — Scaffold (Vanilla / Watchdog / Sprint) · Chest Looter · Disabler · No Fall · Lightning Detector · World Editor

**Visual** — Animations (1.7 block, item physics positions) · Nametags · ESP · Chest ESP · Perspective · Free Look · Fullbright · No Swing Sound · Item Physics

**HUD** — Watermark · Target HUD · Session Info · MediaPlayer · Speedometer · Armor Status · Module List · TabGUI

**Utility** — Inventory Manager · Zoom · Theme · Notifications

### A few standouts

- **Bed Breaker** — tracks every bed on the map (BedESP render option), tunnels through defence to the bed breaking each block with the optimal tool (shears for wool, axe for wood, pickaxe for stone, bare hand otherwise), all with a free camera. Yields to KillAura and aborts the moment you run off or an enemy closes in.
- **MediaPlayer HUD** — a now-playing card driven by the Windows media session, with cover art and time-synced lyrics from [lrclib.net](https://lrclib.net).
- **Session Info HUD** — time played, kills and wins, with the server favicon and the real advertised server name even behind a proxy.
- **Sprint Scaffold** — fast snap-rotation bridging that renders a clean backward walk on your own model while the server sees the real placement rotation.

## Building

Requires **JDK 25**. Fabric Loom pulls everything else.

```bash
git clone https://github.com/cooldood-dev/openadapt.git
cd openadapt
./gradlew build
```

The built mod lands in `build/libs/adaptv3-<version>.jar` (the `-sources` / `-dev` jars can be ignored).

## Installing

1. Install **Fabric Loader 0.20.1+** for **Minecraft 26.3**.
2. Put **[Fabric API](https://modrinth.com/mod/fabric-api)** and `adaptv3-<version>.jar` in your `mods` folder.
3. Launch with a **Java 25** runtime.

Open the ClickGUI with **Right Shift** (default), or use chat commands like `.help`, `.config`, `.bind` and `.toggle`.

## Configs

- `.config save <name>` / `.config load <name>` / `.config list` — manage local configs.
- In the config menu, the **Online** tab lists shared configs from the `adapt-config` repo; clicking one downloads and auto-loads it.
- Loading any config never changes your keybinds.

## Tech notes

- Package `dev.adapt.v3`, built with Fabric Loom against Minecraft 26.3 (Mojang mappings).
- Rendering uses the 26.3 `GuiGraphics` pipeline with custom WGSL shaders for backdrop blur, the menu nebula and mask-outline ESP.
- Mixins live in `src/main/java/dev/adapt/v3/mixin`; events are dispatched through a lightweight `@Subscribe` bus.

## Credits

Fonts and icons are used under their own licenses (see `src/main/resources/licenses`): Poppins and Inter (SIL OFL) and Google Material Icons (Apache 2.0).

## License

Released under the [MIT License](LICENSE).
