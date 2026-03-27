# Harder Wardens — Fabric Mod

Fabric mod based on the HarderWardens Spigot plugin by JustErikSK.

Wardens gain more HP, deal more damage, and drop better loot. Fully configurable via a single JSON file.

---

## Config (`config/harder_wardens.json`)

Generated automatically on first launch:

```json
{
  "difficulty": "NORMAL",
  "customHealth": 500.0,
  "customDamageMultiplier": 1.5,
  "customLootPreset": "NORMAL"
}
```

### `difficulty`

| Value       | HP   | Damage | Loot drops |
|-------------|------|--------|------------|
| `EASY`      | 500  | 1.0x   | 1-3 Echo Shards |
| `NORMAL`    | 500  | 1.5x   | 2-5 Echo Shards, 1 Sculk Catalyst |
| `HARD`      | 750  | 2.0x   | 3-7 Echo Shards, 1-2 Sculk Catalyst, 1-3 Diamond |
| `NIGHTMARE` | 850  | 2.5x   | 5-10 Echo Shards, 1-3 Sculk Catalyst, 1-3 Diamond, 1-2 Netherite Scrap |
| `INSANE`    | 1000 | 3.0x   | 7-15 Echo Shards, 2-5 Sculk Catalyst, 2-4 Diamond, 1-3 Netherite Scrap, 1-2 Netherite Ingot |
| `CUSTOM`    | ->   | ->     | Uses the custom fields below |

> Vanilla Warden baseline: 500 HP, 30 base attack damage.
> Minecraft 26.1 clamps `minecraft:max_health` to `1024`, so presets stay below that vanilla limit.

### `customHealth` *(CUSTOM only)*
Maximum HP for the Warden. Default: `500.0`

### `customDamageMultiplier` *(CUSTOM only)*
Multiplier on the Warden's base attack damage. Default: `1.5`
Example: `2.0` = double damage.

### `customLootPreset` *(CUSTOM only)*
Which loot table preset to use: `NONE`, `EASY`, `NORMAL`, `HARD`, `NIGHTMARE`, or `INSANE`

`NONE` disables all extra loot drops.

---

## Commands

| Command                 | Permission | Description |
|-------------------------|------------|-------------|
| `/harderwardens reload` | OP (lvl 2) | Reloads the config for future Warden spawns |
| `/harderwardens info`   | OP (lvl 2) | Shows current difficulty settings |

> Existing Wardens keep their current stats after `/harderwardens reload`.
> Loot table changes still require `/reload` or a restart.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 26.1.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api).
3. Drop `harder-wardens-<version>.jar` into your `mods/` folder.
4. Start the server/client. The config is created at `config/harder_wardens.json`.
5. Edit the config and restart, or use `/harderwardens reload`.

This mod is also available on [Modrinth](https://modrinth.com/mod/harder-wardens-mod) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/harder-wardens).

---

## Building

```bash
git clone https://github.com/SwordfishBE/HarderWardens-mod.git
cd HarderWardens-mod
./gradlew build
```

Output JAR appears in `build/libs/`.

This mod targets Minecraft `26.1` with Fabric Loader `0.18.4`, Fabric API `0.144.0+26.1`, Fabric Loom `1.15-SNAPSHOT`, and Java `25` for the Gradle JVM.

---

## License

AGPL-3.0 — see [LICENSE](LICENSE)
