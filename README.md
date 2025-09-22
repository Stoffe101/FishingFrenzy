# Fishing Frenzy

Fishing Frenzy is a Minecraft plugin for Paper 1.21.8 that adds a timed fishing event with special loot, streak/luck mechanics, and a global meter. It ships as a single shaded JAR (no extra libraries needed).

## Requirements
- Server: Paper 1.21.8 (compatible with modern 1.21.x)
- Java: 21 (LTS)

## Build (Windows)
Use the included Gradle wrapper to build a shaded JAR that already contains Kyori Adventure and MiniMessage:

```
cmd
./gradlew.bat clean build
```

Output:
- `build\libs\FishingFrenzy-1.0.0.jar` (shaded, deploy this)

Optional: run a local test server that auto-loads your plugin
```
cmd
./gradlew.bat runServer
```

## Install/Upgrade
1. Stop your server.
2. Remove any Adventure libraries you previously put into `plugins` (e.g. `adventure-api-*.jar`, `adventure-platform-bukkit-*.jar`, `adventure-text-minimessage-*.jar`) and any related files under `plugins\.paper-remapped\`.
3. Copy `build\libs\FishingFrenzy-1.0.0.jar` into your server's `plugins` folder.
4. If upgrading from an older config, delete `plugins/FishingFrenzy/config.yml` once to regenerate the corrected default file, then customize it again.
5. Start the server.

## Commands & Permissions
- `/frenzy reload` — Reloads the plugin configuration
- `/frenzy status` — Shows current state (active, time left / time until next)
- `/frenzy start` — Starts a frenzy immediately (bypasses meter), and resets the global cooldown
- `/frenzy end` — Ends the current frenzy early, if one is active
- Permission: `fishingfrenzy.command` (default: op)

Commands are declared in `plugin.yml` (Bukkit-style). This plugin does not use `paper-plugin.yml` for commands.

## Features
- Timed “Frenzy” event with countdowns and boss bar
- Global meter (optional) requiring a number of catches to trigger Frenzy
- Streaks increase luck per catch up to a configurable cap
- Pity system guarantees a spicy drop after N misses during Frenzy
- Weighted loot table with custom names, lore, and enchantments
- Optional "Lucky Rod" that doubles spicy chance during Frenzy

## Configuration
Default config is written to `plugins/FishingFrenzy/config.yml` on first run. Key options:

- `frenzy.duration` (int, seconds) — Default 90
- `frenzy.cooldown` (int, seconds) — Default 3600 (1 hour). For 30 minutes, set to `1800`.
- `frenzy.per-player-cooldown` (int, seconds) — Default 600
- `frenzy.loot-multiplier` (double) — Global multiplier applied to chances
- `frenzy.mode` (string) — `global` (current)
- `frenzy.allowed-worlds` (string list) — Allowed world names; empty = all
- `frenzy.allowed-biomes` (string list) — Allowed biome names; empty = all
- `frenzy.allowed-times` (string list) — `DAY` and/or `NIGHT`; empty = both
- `frenzy.meter.enabled` (bool) — Enable global meter
- `frenzy.meter.required-fish` (int) — Catches required to trigger Frenzy
- `frenzy.streaks.luck-per-streak` (double) — Luck increase per streak
- `frenzy.streaks.max-streak` (int) — Streak cap
- `frenzy.pity-threshold` (int) — Guarantee spicy after N misses
- `frenzy.loot-table` (list of items) — Weighted entries with fields:
  - `type` — Bukkit material name (e.g., `DIAMOND`, `FISHING_ROD`)
  - `weight` — Relative weight (int)
  - `amount` — Either a number (e.g., `1`) or range string `min-max` (e.g., `2-4`)
  - `spicy` — true/false to mark spicy drops
  - `enchants` — List of enchantment names (applies to enchanted book or gear)
  - `name` — MiniMessage string for display name
  - `lore` — List of MiniMessage strings for lore

Notes:
- MiniMessage strings must be valid YAML scalars. Quote strings that contain `:` or special characters, and ensure tags are balanced (e.g., `"<gradient:gold:yellow>Lucky Rod</gradient>"`).
- The plugin reads `allowed-biomes` even if it’s not present; keeping it in the config improves clarity.

## Troubleshooting
- YAML parse error on startup
  - Typically caused by malformed quotes or unbalanced MiniMessage tags in `config.yml`. Delete the server's `plugins/FishingFrenzy/config.yml` to regenerate the default, then reapply your changes carefully.
- Duplicate boss bars after reload
  - Fixed in code: reloading now hides and replaces the boss bar. If you still see two, update the plugin JAR to this build and try `/frenzy reload` again.
- "Does not contain plugin.yml/paper-plugin.yml"
  - Remove any non-plugin library JARs from `plugins` (Adventure jars). Only keep `FishingFrenzy-1.0.0.jar`.
- Command not found
  - Ensure you’re using this shaded build and that `plugin.yml` is packaged (it is by default). Check console for enable errors.

## Development Notes
- Build: shaded via `com.gradleup.shadow` (relocates Kyori to `com.example.fishingFrenzy.shaded.*`).
- Commands: declared in `src/main/resources/plugin.yml` and wired via `JavaPlugin#getCommand` in `FishingFrenzy`.
- Local test server: `./gradlew.bat runServer` (defaults to 1.21; adjust as needed).

## License
Provided as-is. You may modify and redistribute as you wish.
