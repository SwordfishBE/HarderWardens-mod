# Harder Wardens — Fabric Mod

Fabric mod based on the HarderWardens Spigot plugin by JustErikSK.

Wardens gain more HP, deal more damage, and drop better loot. Fully configurable via a single JSON file.

---

## ⚙️ Config (`config/harder_wardens.json`)

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

| Value       | HP   | Damage | Loot drops                               |
|-------------|------|--------|------------------------------------------|
| `EASY`      | 500  | 1.0×   | 1–3 Echo Shards                          |
| `NORMAL`    | 500  | 1.5×   | 2–5 Echo Shards, 1 Sculk Catalyst        |
| `HARD`      | 750  | 2.0×   | + 1–2 Sculk Catalyst, 1–3 Diamond       |
| `NIGHTMARE` | 1000 | 2.5×   | + 1–3 Diamond, 1–2 Netherite Scrap      |
| `INSANE`    | 1500 | 3.0×   | + 1–3 Netherite Scrap, 1–2 Netherite Ingot |
| `CUSTOM`    | →    | →      | → see fields below (supports `NONE` loot) |

> Vanilla Warden baseline: 500 HP, 30 base attack damage (45 on Hard game difficulty).

### `customHealth` *(CUSTOM only)*
Maximum HP for the Warden. Default: `500.0`

### `customDamageMultiplier` *(CUSTOM only)*
Multiplier on the Warden's base attack damage. Default: `1.5`
Example: `2.0` = double damage (60 base → 90 on Hard game difficulty)

### `customLootPreset` *(CUSTOM only)*
Which loot table preset to use: `NONE`, `EASY`, `NORMAL`, `HARD`, `NIGHTMARE`, or `INSANE`

`NONE` disables all extra loot drops — the Warden will only drop its vanilla loot (nothing, by default).

---

## 🎮 Commands

| Command                   | Permission | Description                         |
|---------------------------|------------|-------------------------------------|
| `/harderwardens reload`   | OP (lvl 2) | Reloads the config from disk |
| `/harderwardens info`     | OP (lvl 2) | Shows current difficulty settings   |

> **Note:** Loot table changes require a `/reload` or server restart after `/harderwardens reload`.

---

## 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft
2. Download [Fabric API](https://modrinth.com/mod/fabric-api)
3. Drop `harder-wardens-<version>.jar` into your `mods/` folder
4. Start the server/client — config is created at `config/harder_wardens.json`
5. Edit the config and restart, or use `/harderwardens reload`

---

## 🔨 Building

```bash
git clone https://github.com/SwordfishBE/HarderWardens-mod.git
cd HarderWardens-mod
chmod +x gradlew
./gradlew build
```

Output JAR appears in `build/libs/`.

> This mod targets the unobfuscated Minecraft `26.1` toolchain via Fabric Loom `1.15-SNAPSHOT`
> and requires Java `25` for the Gradle JVM.

---

## License

AGPL-3.0 — see [LICENSE](LICENSE)
