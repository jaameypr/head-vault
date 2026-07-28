# HeadVault architecture

The design priority, after correctness, is **update speed**: porting to a new Minecraft version
should touch one package. This document explains how that is achieved.

## Package map

```
it.pruefert.headvault
├─ HeadVault                 ModInitializer; wires access modes + lifecycle
├─ HeadVaultRuntime          live, swappable state (config/catalog/prices) + shop operations
├─ catalog/                  ── pure Java, NO Minecraft imports (unit-tested) ──
│   ├─ Head, HeadCategory, CategorizedHead, HeadCatalog
│   ├─ HeadJson              parse/serialize the minecraft-heads array shape
│   ├─ CatalogStore          disk cache: atomic validated writes, TTL, corrupt→bundled recovery
│   ├─ refresh/CatalogRefreshService   async, parallel-per-category, timeout, atomic snapshot swap
│   └─ source/               HeadSource: MinecraftHeadsV1Source | V2Source | BundledSource
├─ economy/                  ── pure Java (unit-tested) ──
│   ├─ EconomyMode, Cost, CategoryPricing, PriceResolver, XpMath, CostFormat, PurchaseResult
│   └─ EconomyService        orchestrates payment (touches Minecraft via compat)
├─ config/                   ── pure Java (unit-tested) ──  HeadVaultConfig (Gson) + ConfigManager
├─ permission/Perms          permission nodes + fabric-permissions-api checks
├─ event/PurchaseEvent       Fabric Event hook for other mods
├─ ui/                       sgui screens: ShopRootGui, HeadGridGui, SearchInputGui
├─ access/                   command/ · villager/ · npc/   (the three access modes)
└─ compat/   ★ THE ONLY PACKAGE A VERSION BUMP SHOULD TOUCH ★
    ├─ HeadStacks            player-head/icon item building (profile component — #1 churn point)
    ├─ McItems               registry/identifier lookups
    ├─ GuiText               chat-component construction + styling
    ├─ PlayerEconomy         inventory + experience primitives
    └─ EntityInteractions    right-click event binding (UseEntityCallback / InteractionResult)
```

## The update-speed strategy

Two rules keep version churn contained:

1. **`catalog`, `economy`, and `config` import zero Minecraft types.** They model heads, prices, XP
   math, caching, and configuration as plain Java. This is why they are fully unit-testable with no
   game runtime — and why a Minecraft API change can never reach them.

2. **Every version-sensitive Minecraft call lives in `compat`.** The UI and access layers call
   `HeadStacks`, `McItems`, `GuiText`, `PlayerEconomy`, and `EntityInteractions` instead of touching
   `net.minecraft.*` directly. When Minecraft moves an API, the break surfaces in `compat`, gets
   fixed in one place, and the rest of the mod is unaffected.

26.1 alone validated this: it renamed `ResourceLocation`→`Identifier`, made `ResolvableProfile`
abstract (factory instead of constructor), turned authlib `GameProfile` into a record
(`properties()` not `getProperties()`), renamed `moveTo`→`snapTo` and `getTags`→`entityTags`, and
restructured interaction events. All of that is absorbed by `compat`.

The mod now builds for **multiple Minecraft versions at once** from this same source tree, via
[Stonecutter](https://github.com/stonecutter-mc/stonecutter) 0.9.6 (Kotlin DSL):

- `settings.gradle.kts` declares the version nodes — `versions("26.1.2", "26.2")`, with
  `vcsVersion = "26.1.2"` as the checked-in active node.
- `stonecutter.properties.toml` holds global keys (`mod.version`, `deps.fabric_loader`,
  `deps.permissions`) plus one table per version (`["26.1.2"]`, `["26.2"]`) for the values that
  differ: `deps.fabric_api`, `deps.sgui`, `mod.mc_compat`, `mod.mc_releases`.
- `build.gradle.kts` is a single shared build script that every node runs; it reads the
  per-version values via `sc.properties.get<String>("...")`. The Loom plugin (`net.fabricmc.fabric-loom`)
  runs unmapped for both targets — 26.1.2 and 26.2 are both unobfuscated, so there's still no
  mappings line.
- `./gradlew build` builds the active node; `./gradlew chiseledBuild` builds **every** node in one
  pass, collecting each jar to `build/libs/<mod.version>/`. The release workflow injects the mod
  version from the git tag into `stonecutter.properties.toml` before running `chiseledBuild`.

`compat` now serves multiple simultaneous targets, so where the Minecraft APIs for 26.1.2 and 26.2
diverge, the difference is guarded *inside* `compat` with a Stonecutter `//?` conditional comment
instead of being solved by branching the whole package. For example, `compat.McItems.villagerType()`
guards `EntityType.VILLAGER` (26.1.2) vs. `EntityTypes.VILLAGER` (26.2) behind one `//?` block —
one method serves both versions, and nothing that calls it needs to know.

## Data flow

```
HeadSource (v1 | v2 | bundled)
   │  async, off the server thread
   ▼
CatalogRefreshService ── parallel per-category fetch, timeout + retry
   │   temp file → JSON validate → atomic rename   (corrupt downloads can't clobber good cache)
   ▼
CatalogStore (config/headvault/cache/*.json + .last-refresh)
   │  load (disk → bundled fallback per category)
   ▼
HeadCatalog (immutable, indexed)  ◄── volatile reference, swapped atomically; read lock-free
   │
   ▼
ShopRootGui → HeadGridGui ──click──► HeadVaultRuntime.buy()
                                        │  re-check cost now + atomic deduct (EconomyService)
                                        ▼ success
                                  HeadStacks.playerHead() → deliver → PurchaseEvent → log
```

## Threading model

All catalog work runs on a dedicated daemon pool, never on the server tick thread:

- On `SERVER_STARTED`, the cached/bundled catalog is published immediately (the shop works at
  once), then a network refresh runs **only if the cache is older than `refreshIntervalHours`**.
- Each category is fetched concurrently with a per-task timeout; a failure keeps that category's
  previous cache file rather than blanking it.
- A completed refresh builds a new `HeadCatalog` and swaps a `volatile` reference; readers on the
  tick thread never lock.
- `/headvault reload` rebuilds config and restarts the refresh service in place.

## Access modes

All three funnel into `HeadVaultRuntime.openShop(player)`:

- **Command** — Brigadier `/heads`, permission-gated (dynamic, so reloads take effect).
- **NPC** — real villagers tagged with the `headvault.npc` scoreboard tag. Being real entities they
  persist in chunk NBT automatically; the tag identifies them after restart with **no custom
  world-storage** (26.1 changed that API heavily, so we avoid it). They are invulnerable, AI-less,
  and persistence-required.
- **Named villager** — any villager whose custom name matches the configured string.

NPC and named-villager interactions are detected through `compat.EntityInteractions` (a single
`UseEntityCallback` binding); returning a consumed result cancels the vanilla villager trade GUI.
That same binding also drives the optional **freeze** (`access.villager.freeze`): when a name tag is
held, `access.villager.TraderFreeze` predicts the resulting name from the item and locks the mob in
place (disables AI, marked with the `headvault.frozen` tag) if it becomes a trader, reverting it on
a rename that no longer matches — while vanilla still performs the actual rename.

## Testing

`catalog`, `economy`, and `config` are pure, so JUnit 5 covers the parts most worth protecting with
no Minecraft runtime: cache TTL / offline fallback / corrupt-file recovery / atomic-write rejection,
price resolution with per-category overrides, XP level↔points math, cost formatting, JSON
parse/serialize, and config parsing/recovery.
