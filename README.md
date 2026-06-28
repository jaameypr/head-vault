<div align="center">

# 🗿 HeadVault

**A server-side custom-heads shop for Minecraft Fabric SMPs.**
Browse 90,000+ decorative player heads in a clean chest GUI — open it with a command, a shopkeeper NPC, or a named villager. No client mods. No resource packs. Vanilla players just play.

`Minecraft 26.1.2` · `Fabric` · `Java 25` · `Server-side only`

</div>

---

## ✨ Why HeadVault?

Custom heads make builds come alive — but handing them out one command at a time is painful. HeadVault turns the entire [minecraft-heads.com](https://minecraft-heads.com) catalog into an in-game shop your players can browse and buy from, however you want to run it: a command, a placed-down NPC, or a villager you name. It’s **server-side only**, so nobody needs to install anything to use it.

It’s also built to **survive Minecraft updates**. Every version-fragile piece of code is quarantined in one package, so porting to the next MC release is usually a five-line version bump. (Details in [ARCHITECTURE.md](ARCHITECTURE.md).)

## 🎯 Features at a glance

- 🛒 **A real shop GUI** — categories → paginated head grid → click to buy. Renders natively on vanilla clients.
- 🌍 **The whole catalog** — ~90k heads across 10 categories, fetched live and cached to disk. Refreshes in the background; **never lags the server**.
- 🔌 **Always works offline** — unreachable API? It serves the last cache, then a snapshot baked into the jar.
- 🔎 **Search** — find heads by name or tag across the entire catalog.
- 🙂 **Player heads** — type any username and get that player’s head as a clickable result.
- 🚪 **Three ways in** — `/heads` command, persistent shopkeeper **NPCs**, and **named villagers**. Mix and match.
- 💰 **Flexible economy** — free, item cost, or experience (levels/points), with **per-category pricing**. Purchases are atomic.
- 📦 **Heads stack** — identical heads stack to 64.
- ♻️ **Hot reload** — tweak the config and run `/headvault reload`. No restart.
- 🔐 **Permissions** — LuckPerms-compatible, with sane vanilla-OP fallbacks when no permission mod is present.
- 🪝 **Extensible** — a `PurchaseEvent` other mods can listen to.

## 🚀 Getting started

1. **Requirements:** a Fabric **26.1.2** server on **Java 25**, with [Fabric API](https://modrinth.com/mod/fabric-api) installed.
2. Drop `head-vault-<version>.jar` into your server’s `mods/` folder. *(sgui and fabric-permissions-api are bundled inside — nothing else to install.)*
3. Start the server. HeadVault writes its config to `config/headvault/config.json` and begins downloading the head catalog in the background.
4. Run **`/heads`** — and you’re shopping. 🎉

> First launch shows a small built-in sample until the full catalog finishes downloading (a few seconds).

## 🚪 Access modes

Enable any combination in the config.

| Mode | How it works |
|---|---|
| **Command** | `/heads` opens the shop (permission-gated). |
| **NPC** | `/headvault npc spawn <name>` places an invulnerable, AI-less shopkeeper villager. It persists across restarts; right-click opens the shop. Manage with `npc list` / `npc remove`. |
| **Named villager** | Name a villager (or, via `access.villager.mode`, any mob) your configured name (default **”Head Trader”**) and right-clicking opens the shop for **everyone** — no permission needed. Hold a name tag to rename it back to normal. **Since 1.1.0:** optionally **freeze** new traders in place (disables AI) via `access.villager.freeze`. |

## 💰 Economy

Pick one global mode (with optional per-category overrides):

| Mode | Players pay… |
|---|---|
| `FREE` | nothing |
| `ITEM` | a configurable item × amount (e.g. 1 diamond) |
| `XP_LEVELS` | experience levels |
| `XP_POINTS` | total experience points |

Every purchase re-checks the cost at click time and deducts atomically — players can never be charged without receiving their head. Player-name heads use the **global** price. Grant `headvault.free-bypass` to let someone shop for free.

## ⌨️ Commands

| Command | Description | Permission *(default)* |
|---|---|---|
| `/heads` | Open the shop | `headvault.command.use` *(OP 2)* |
| `/heads search <query>` | Search heads by name/tag | `headvault.command.search` |
| `/heads player <name>` | Get a specific player’s head | `headvault.command.use` |
| `/heads give <player> <head-id>` | Give a head for free *(head-id = its UUID)* | `headvault.command.give` / `headvault.admin` |
| `/headvault reload` | Reload the config | `headvault.admin` |
| `/headvault npc spawn <name>` | Spawn a shop NPC where you stand | `headvault.npc.manage` |
| `/headvault npc remove` | Remove the NPC you’re looking at | `headvault.npc.manage` |
| `/headvault npc list` | List loaded shop NPCs | `headvault.npc.manage` |

## 🔐 Permissions

| Node | Grants |
|---|---|
| `headvault.command.use` | Open the shop, `/heads player` |
| `headvault.command.search` | `/heads search` |
| `headvault.command.give` | `/heads give` |
| `headvault.admin` | `/headvault reload`, `/heads give` |
| `headvault.npc.manage` | Spawn / remove / list NPCs |
| `headvault.free-bypass` | Always pay nothing |

**No permission mod?** Command nodes fall back to the vanilla OP level, and NPC / named-villager modes work for everyone without any permission. **With LuckPerms** (or any provider) grant nodes normally:

```
/lp group default permission set headvault.command.use true
```

<a id="configuration"></a>

## ⚙️ Configuration

The config lives at **`config/headvault/config.json`** and hot-reloads with `/headvault reload`. *(It’s JSON, not YAML — Gson under the hood.)* Here’s the full file with every option:

```jsonc
{
  "_schemaVersion": 1,

  "catalog": {
    "source": "v1",                 // where heads come from: "v1" | "v2" | "bundled"
    "refreshIntervalHours": 24,      // how often to re-download the catalog
    "requestTimeoutSeconds": 15,     // per-request network timeout
    "maxRetries": 2,                 // retries per category before giving up
    "v2AppUuid": "",                 // v2 only: your registered App UUID
    "v2UrlTemplate": "",             // v2 only: custom endpoint; placeholders {category}, {appUuid}
    "userAgent": "Mozilla/5.0 ... Chrome/126.0.0.0 Safari/537.36"  // sent with requests (see note)
  },

  "economy": {
    "mode": "FREE",                  // "FREE" | "ITEM" | "XP_LEVELS" | "XP_POINTS"
    "item": { "id": "minecraft:diamond", "amountPerHead": 1 },
    "xp":   { "amountPerHead": 1 },
    "categoryOverrides": {
      // override price per category (key = category slug):
      // "monsters": { "mode": "ITEM", "item": { "id": "minecraft:netherite_ingot", "amountPerHead": 1 } }
    }
  },

  "access": {
    "command":  { "enabled": true, "permissionLevel": 2 },
    "npc":      { "enabled": true },
    "villager": {
      "enabled": true,
      "name": "Head Trader",
      "caseInsensitive": true,
      "mode": "ONLY_VILLAGER",        // which mobs can be a named trader: "ONLY_VILLAGER" | "ALL" | "MOB_WHITELIST" | "MOB_BLACKLIST"
      "entityWhitelist": [],          // entity type ids allowed when mode = "MOB_WHITELIST", e.g. ["minecraft:zombie"]
      "entityBlacklist": [],          // entity type ids blocked when mode = "MOB_BLACKLIST"
      "freeze": false                 // since 1.1.0: freeze a name-tagged trader in place (disables AI)
    }
  },

  "ui": {
    "title": "HeadVault",            // shop window title
    "showPriceInLore": true,         // show the price on each head
    "headsPerPage": 45               // 9–45 heads per page
  },

  "logging": { "purchaseVerbosity": "INFO" }   // "OFF" | "INFO" | "DEBUG"
}
```

### Field reference

| Key | Default | What it does |
|---|---|---|
| `catalog.source` | `"v1"` | `v1` = the no-token endpoint serving the full catalog (recommended). `v2` = the licensed REST API (needs an App UUID). `bundled` = the offline snapshot only. |
| `catalog.refreshIntervalHours` | `24` | Background refresh interval. The catalog is only re-downloaded when older than this. |
| `catalog.requestTimeoutSeconds` / `maxRetries` | `15` / `2` | Network resilience knobs. |
| `catalog.v2AppUuid` / `v2UrlTemplate` | `""` | Only used when `source` is `v2`. Register at minecraft-heads.com, then set the UUID; adjust the template to match the official v2 docs if needed. |
| `catalog.userAgent` | browser string | minecraft-heads.com **rejects non-browser agents (HTTP 403)**, so the default mimics a browser. Leave it unless you know the API accepts yours. |
| `economy.mode` | `"FREE"` | Global payment mode. |
| `economy.item` / `economy.xp` | diamond×1 / 1 | Global price for `ITEM` / `XP_*` modes. |
| `economy.categoryOverrides` | `{}` | Per-category price overrides, keyed by slug (`alphabet`, `animals`, `blocks`, `decoration`, `food-drinks`, `humanoid`, `humans`, `miscellaneous`, `monsters`, `plants`). |
| `access.command.enabled` / `permissionLevel` | `true` / `2` | Toggle the `/heads` shop and its default OP level. |
| `access.npc.enabled` | `true` | Toggle NPC shopkeepers. |
| `access.villager.enabled` / `name` / `caseInsensitive` | `true` / `Head Trader` / `true` | Toggle named-trader mode, the trigger name, and case-sensitivity of the name match. |
| `access.villager.mode` | `"ONLY_VILLAGER"` | Which entity types may be a named trader: `ONLY_VILLAGER` (villagers only — default, unchanged behavior), `ALL` (any mob), `MOB_WHITELIST` (only ids in `entityWhitelist`), `MOB_BLACKLIST` (any mob except ids in `entityBlacklist`). |
| `access.villager.entityWhitelist` / `entityBlacklist` | `[]` / `[]` | Entity type ids (e.g. `"minecraft:zombie"`; a bare `"zombie"` assumes the `minecraft` namespace) used by the `MOB_WHITELIST` / `MOB_BLACKLIST` modes. |
| `access.villager.freeze` *(since 1.1.0)* | `false` | When a name tag turns a mob into a trader, freeze it in place — disables AI so it stops wandering, like a spawned NPC. Reverts automatically when the mob is renamed so it no longer qualifies. Off (default) = unchanged behavior. |
| `ui.title` / `showPriceInLore` / `headsPerPage` | `HeadVault` / `true` / `45` | Cosmetic shop options. |
| `logging.purchaseVerbosity` | `"INFO"` | How loudly purchases are logged to console. |

### About the catalog source

minecraft-heads.com offers two APIs:

- **`v1`** *(default)* — the `scripts/api.php` endpoint. **No token**, serves the entire catalog. Officially “deprecated” but live as of 2026; the site blocks non-browser User-Agents, which is why the default `userAgent` looks like a browser.
- **`v2`** — the current licensed REST API. Requires a free **registered App UUID** and a visible Minecraft-Heads.com attribution; commercial redistribution isn’t allowed without approval. Switch to this if v1 is ever retired.
- **`bundled`** — never touches the network; serves the small snapshot baked into the jar.

If the live source is unreachable, HeadVault automatically falls back to the last cache, then the bundled snapshot — the shop never goes dark.

## 🔄 Updating to a new Minecraft version

HeadVault is designed to make this trivial:

1. Edit the version lines in **`gradle.properties`** (`minecraft_version`, `loader_version`, `fabric_api_version`, `sgui_version`, `permissions_version`). Check current values at [fabricmc.net/develop](https://fabricmc.net/develop).
2. Run `./gradlew build`.
3. If it compiles, you’re done. If anything breaks, it’ll be in the **`compat` package** — the single place that touches version-sensitive Minecraft APIs. Fix it there; nothing else needs changing.

## 🛠️ Building from source

```bash
./gradlew build        # -> build/libs/head-vault-<version>+26.1.2.jar  (sgui + permissions bundled)
./gradlew runServer    # launch a dev server to try it out
```

Requires JDK 25 on your `PATH`.

## 📦 Releases & CI

- Branches: `master` (stable), `experimental` (preview). Every push/PR builds and uploads an artifact.
- Tag `vX.Y.Z` → stable GitHub Release + jar. A tag with a `-` suffix (e.g. `v1.2.0-experimental.1`) → pre-release.
- **The Minecraft version is always visible:** jars are named `head-vault-<modversion>+<mcversion>.jar` (e.g. `head-vault-1.0.0+26.1.2.jar`), the in-game mod version reads `1.0.0+26.1.2`, and the GitHub Release is titled `vX.Y.Z — Minecraft 26.1.2`. So `v1.0.0` alone never leaves players guessing.
- Modrinth/CurseForge publishing is wired into the release workflow and ready to enable.

## 🙌 Credits

- Head data from [minecraft-heads.com](https://minecraft-heads.com).
- [sgui](https://github.com/Patbox/sgui) & [fabric-permissions-api](https://github.com/lucko/fabric-permissions-api) for GUIs and permissions.
- Inspired by [HeadIndex](https://github.com/PotatoPresident/HeadIndex).

## 📄 License

Released under the [MIT License](LICENSE).
