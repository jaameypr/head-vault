# HeadVault architecture

The design priority, after correctness, is **update speed**: porting to a new Minecraft version
should touch one package. This document explains how that is achieved.

## Package map

```
io.github.jaameypr.headvault
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

`gradle.properties` is the version control panel — five lines pin Minecraft, Loader, Fabric API,
sgui, and permissions. The release workflow reads the mod version from the git tag.

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

## Testing

`catalog`, `economy`, and `config` are pure, so JUnit 5 covers the parts most worth protecting with
no Minecraft runtime: cache TTL / offline fallback / corrupt-file recovery / atomic-write rejection,
price resolution with per-category overrides, XP level↔points math, cost formatting, JSON
parse/serialize, and config parsing/recovery.
