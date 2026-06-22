# Configurable Trader Entity-Type Filter

**Date:** 2026-06-22
**Branch:** `feat/allow-all-traders`
**Status:** Approved design

## Problem

The "named trader" access mode only works on vanilla villagers. `NamedVillagerListener.matches()`
hard-checks `entity instanceof Villager`, so a name-tagged zombie, wandering trader, or any other
mob cannot carry the HeadVault shop GUI even if it has the configured trader name.

The feature adds a configurable state that controls **which mob types** may be name-tagged and
carry the HeadGui, via four modes: `ONLY_VILLAGER`, `ALL`, `MOB_WHITELIST`, `MOB_BLACKLIST`.

## Goals

- Let server owners choose which entity types qualify as a named HeadVault trader.
- Default behavior is unchanged (`ONLY_VILLAGER` == today's villager-only logic).
- Keep the eligibility decision pure and unit-testable; keep version-sensitive Minecraft registry
  access confined to the `compat` package.
- Update README (Config + Feature/Access-modes sections) and produce updated Modrinth description text.

## Non-Goals (YAGNI)

- No per-type custom names.
- No in-game GUI/command to edit the whitelist/blacklist.
- No new "auto-nametag" command — naming a mob is still done with a vanilla name tag.
- No change to NPC mode or the name-tag-in-hand guard.

## Design

### 1. Config — additive change to `access.villager`

No rename, no schema-version bump. Three new fields added to
`HeadVaultConfig.Access.Villager`:

```jsonc
"villager": {
  "enabled": true,
  "name": "Head Trader",
  "caseInsensitive": true,
  "mode": "ONLY_VILLAGER",          // ONLY_VILLAGER | ALL | MOB_WHITELIST | MOB_BLACKLIST
  "entityWhitelist": [],            // entity type ids, used when mode = MOB_WHITELIST
  "entityBlacklist": []             // entity type ids, used when mode = MOB_BLACKLIST
}
```

- `mode` — `String`, default `"ONLY_VILLAGER"`.
- `entityWhitelist` — `List<String>`, default empty.
- `entityBlacklist` — `List<String>`, default empty.

Existing config files that lack these fields deserialize with the defaults, yielding behavior
identical to the current release.

### 2. `TraderMode` enum (pure)

Lives in package `it.pruefert.headvault.access.villager`, mirroring the `EconomyMode` pattern.

```java
public enum TraderMode {
    ONLY_VILLAGER, ALL, MOB_WHITELIST, MOB_BLACKLIST;

    public static TraderMode parse(String raw);                  // null/invalid -> ONLY_VILLAGER
    public boolean allows(String entityTypeId,
                          List<String> whitelist,
                          List<String> blacklist);
}
```

- `parse` is case-insensitive, trims, and falls back to `ONLY_VILLAGER` on null or unknown values
  (same contract as `HeadVaultConfig.parseMode` for economy).
- `allows`:
  - `ONLY_VILLAGER` → `"minecraft:villager".equals(normalized id)` (equivalent to today's
    `instanceof Villager` — `WanderingTrader`/zombie villager are distinct types and excluded, as now).
  - `ALL` → `true`.
  - `MOB_WHITELIST` → normalized id is present in the normalized whitelist.
  - `MOB_BLACKLIST` → normalized id is **not** present in the normalized blacklist.
- **Id normalization** (pure, no Minecraft types): trim, lowercase (`Locale.ROOT`), and prepend
  `minecraft:` when no `:` namespace is present. Applied to both the incoming id and every list
  entry, so `"Zombie"`, `"zombie"`, and `"minecraft:zombie"` all match.

This class contains all branching logic and is fully unit-testable without a Minecraft runtime.

### 3. `McEntities` compat helper

New class `it.pruefert.headvault.compat.McEntities`, parallel to `McItems`:

```java
public static String typeId(Entity entity) {
    return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
}
```

Returns ids like `"minecraft:villager"`. This is the only Minecraft-registry touchpoint added by
the feature, keeping version-sensitive code in `compat`.

### 4. `NamedVillagerListener.matches()` rewrite

```java
public static boolean matches(Entity entity, HeadVaultConfig config) {
    HeadVaultConfig.Access.Villager v = config.access.villager;

    // 1. Name match first (cheap, rejects the common case).
    Component customName = entity.getCustomName();
    if (customName == null) return false;
    if (v.name == null || v.name.isBlank()) return false;
    String actual = customName.getString();
    boolean nameOk = v.caseInsensitive ? actual.equalsIgnoreCase(v.name) : actual.equals(v.name);
    if (!nameOk) return false;

    // 2. Entity-type eligibility per configured mode.
    return TraderMode.parse(v.mode)
            .allows(McEntities.typeId(entity), v.entityWhitelist, v.entityBlacklist);
}
```

- Drops the `instanceof Villager` check and the `Villager` import.
- Name match runs before the registry lookup to keep the hot path cheap.
- Class name and `access.villager` config section retained to avoid churn the change does not
  require; javadoc updated to note it now covers all mob types.

The caller in `HeadVault.onInitialize()` is unchanged: the name-tag-in-hand guard still returns
early so vanilla renaming wins, and `config.access.villager.enabled` still gates the mode.

### 5. Data flow

```
right-click entity (main hand, no name tag held)
  -> EntityInteractions handler
  -> NamedVillagerListener.matches(entity, config)
       -> name equals configured trader name?            (pure-ish, MC Component only)
       -> McEntities.typeId(entity)                       (compat: registry lookup)
       -> TraderMode.parse(mode).allows(id, wl, bl)       (pure)
  -> runtime.openShop(player)                             (unchanged)
```

## Testing

- `TraderModeTest` (pure JUnit, no server):
  - `parse`: each valid value (any case), null → `ONLY_VILLAGER`, garbage → `ONLY_VILLAGER`.
  - `ONLY_VILLAGER`: allows `minecraft:villager`, rejects others.
  - `ALL`: allows arbitrary ids.
  - `MOB_WHITELIST`: allows listed, rejects unlisted; empty list rejects all.
  - `MOB_BLACKLIST`: rejects listed, allows unlisted; empty list allows all.
  - Normalization: `"zombie"` matches `"minecraft:zombie"`; case-insensitive list entries.
- `NamedVillagerListener.matches()` is thin glue over `TraderMode` + `McEntities`; its only
  non-trivial logic (name match) is unchanged, so no new server-dependent test is added.

## Documentation

- **README — Configuration section:** add `mode`, `entityWhitelist`, `entityBlacklist` to the JSON
  example and the field reference table.
- **README — Access modes / Features section:** note the named-trader mode now applies to any mob
  type per the `mode` setting, with the four modes explained.
- **Modrinth description:** no repo file; the updated description text is delivered in chat for
  manual paste into modrinth.com (`mc-publish` does not sync the project body).

## Files Touched

| File | Change |
|------|--------|
| `config/HeadVaultConfig.java` | Add `mode`, `entityWhitelist`, `entityBlacklist` to `Access.Villager`. |
| `access/villager/TraderMode.java` | **New** pure enum + `parse` + `allows`. |
| `compat/McEntities.java` | **New** `typeId(Entity)` registry helper. |
| `access/villager/NamedVillagerListener.java` | Replace `instanceof Villager` with mode-based check. |
| `src/test/.../TraderModeTest.java` | **New** unit tests. |
| `README.md` | Config + Access-modes/Feature sections. |
