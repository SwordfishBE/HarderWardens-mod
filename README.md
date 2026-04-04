# Harder Wardens

Fabric mod based on the HarderWardens Spigot plugin by JustErikSK.

Wardens gain more HP, deal more damage, and drop better loot. Fully configurable!

[![GitHub Release](https://img.shields.io/github/v/release/SwordfishBE/HarderWardens-mod?display_name=release&logo=github)](https://github.com/SwordfishBE/HarderWardens-mod/releases)
[![GitHub Downloads](https://img.shields.io/github/downloads/SwordfishBE/HarderWardens-mod/total?logo=github)](https://github.com/SwordfishBE/HarderWardens-mod/releases)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/YYlTINeM?logo=modrinth&logoColor=white&label=Modrinth%20downloads)](https://modrinth.com/mod/harder-wardens-mod)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1487893?logo=curseforge&logoColor=white&label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/harder-wardens)

---

## ⚙️ Config (`config/harder_wardens.json`)

Generated automatically on first launch. You can edit it directly or use Mod Menu when both Mod Menu and Cloth Config are installed on the client:

```json
{
  // Preset difficulty for newly spawned Wardens.
  // Valid values: EASY, NORMAL, HARD, NIGHTMARE, INSANE, or CUSTOM.
  "difficulty": "NORMAL",

  // Maximum Warden health.
  // Only used when difficulty is CUSTOM.
  "customHealth": 500.0,

  // Multiplies the Warden's base attack damage.
  // Only used when difficulty is CUSTOM.
  "customDamageMultiplier": 1.5,

  // Loot preset used when difficulty is CUSTOM.
  // Valid values: NONE, EASY, NORMAL, HARD, NIGHTMARE, or INSANE.
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
> Minecraft clamps `minecraft:max_health` to `1024`, so presets stay below that vanilla limit.

### `customHealth` *(CUSTOM only)*
Maximum HP for the Warden. Default: `500.0`

### `customDamageMultiplier` *(CUSTOM only)*
Multiplier on the Warden's base attack damage. Default: `1.5`
Example: `2.0` = double damage.

### `customLootPreset` *(CUSTOM only)*
Which loot table preset to use: `NONE`, `EASY`, `NORMAL`, `HARD`, `NIGHTMARE`, or `INSANE`

`NONE` disables all extra loot drops.

---

## 🔄 Commands

| Command                 | Permission | Description |
|-------------------------|------------|-------------|
| `/harderwardens reload` | OP (lvl 2) | Reloads the config for future Warden spawns |
| `/harderwardens info`   | OP (lvl 2) | Shows current difficulty settings |

> Existing Wardens keep their current stats after `/harderwardens reload`.
> Loot table changes still require `/reload` or a restart.

---

## 📦 Installation

| Platform   | Link |
|------------|------|
| GitHub     | [Releases](https://github.com/SwordfishBE/HarderWardens-mod/releases) |
| Modrinth   | [Harder Wardens](https://modrinth.com/mod/harder-wardens-mod) |
| CurseForge | [Harder Wardens](https://www.curseforge.com/minecraft/mc-mods/harder-wardens) |

1. Download the latest JAR from your preferred platform above.
2. Place the JAR in your server's `mods/` folder.
3. Make sure [Fabric API](https://modrinth.com/mod/fabric-api) is also installed.
4. Optional for an in-game config screen on the client: install [Mod Menu](https://modrinth.com/mod/modmenu) and [Cloth Config API](https://modrinth.com/mod/cloth-config).
5. Start Minecraft — the config file will be created automatically.

### Optional Mod Menu support

- With Mod Menu + Cloth Config installed on the client, Harder Wardens exposes a full config screen.
- On dedicated servers, neither Mod Menu nor Cloth Config is required.
- If Mod Menu is present but Cloth Config is missing, the mod still works normally and simply does not open a config GUI.

---

## 🧱 Building

```bash
git clone https://github.com/SwordfishBE/HarderWardens-mod.git
cd HarderWardens-mod
chmod +x gradlew
./gradlew build
```

Output JAR appears in `build/libs/`.

---

## 📄 License

Released under the [AGPL-3.0 License](LICENSE)
