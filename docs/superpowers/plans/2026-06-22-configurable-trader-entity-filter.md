# Configurable Trader Entity-Type Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let server owners choose which mob types can be name-tagged as a HeadVault trader, via a config mode (`ONLY_VILLAGER` / `ALL` / `MOB_WHITELIST` / `MOB_BLACKLIST`), defaulting to today's villager-only behavior.

**Architecture:** All branching lives in a new pure enum `TraderMode` (unit-tested, no Minecraft types). A new `compat` helper `McEntities.typeId(Entity)` does the only version-sensitive registry lookup. `NamedVillagerListener.matches()` drops `instanceof Villager` and delegates the type decision to `TraderMode`. Config gains three additive fields on `access.villager` (no rename, no schema bump).

**Tech Stack:** Java 25, Fabric 26.1.2, Gson config, JUnit 5, Gradle (`./gradlew`).

## Global Constraints

- Package base: `it.pruefert.headvault`.
- `TraderMode` must stay **pure** — no `net.minecraft.*` imports — so it remains unit-testable.
- Version-sensitive Minecraft registry/identifier access goes only in the `compat` package.
- Config change is **additive**: do not rename `access.villager`, do not bump `_schemaVersion`. Missing fields must deserialize to defaults equal to current behavior.
- Default `mode` is `"ONLY_VILLAGER"`, which must behave identically to the current `instanceof Villager` check.
- Commits: plain Conventional-Commit messages. **Do NOT add a `Co-Authored-By: Claude` trailer** (user preference).
- Run tests with `./gradlew test`; full verify with `./gradlew build`.

---

## File Structure

| File | Responsibility |
|------|----------------|
| `src/main/java/it/pruefert/headvault/access/villager/TraderMode.java` | **New.** Pure enum: parse mode string + decide entity-type eligibility. |
| `src/test/java/it/pruefert/headvault/access/villager/TraderModeTest.java` | **New.** Unit tests for all four modes, parsing, normalization. |
| `src/main/java/it/pruefert/headvault/config/HeadVaultConfig.java` | Add `mode`, `entityWhitelist`, `entityBlacklist` to `Access.Villager`. |
| `src/test/java/it/pruefert/headvault/config/ConfigTest.java` | Add defaults + parsing assertions for the new fields. |
| `src/main/java/it/pruefert/headvault/compat/McEntities.java` | **New.** `typeId(Entity)` registry lookup. |
| `src/main/java/it/pruefert/headvault/access/villager/NamedVillagerListener.java` | Replace `instanceof Villager` with `TraderMode` + `McEntities`. |
| `README.md` | Access-modes row, config JSON block, field reference table. |

---

### Task 1: `TraderMode` pure enum

**Files:**
- Create: `src/main/java/it/pruefert/headvault/access/villager/TraderMode.java`
- Test: `src/test/java/it/pruefert/headvault/access/villager/TraderModeTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `static TraderMode TraderMode.parse(String raw)` → never null; null/unknown → `ONLY_VILLAGER`.
  - `boolean TraderMode.allows(String entityTypeId, List<String> whitelist, List<String> blacklist)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/it/pruefert/headvault/access/villager/TraderModeTest.java`:

```java
package it.pruefert.headvault.access.villager;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraderModeTest {

    @Test
    void parseIsCaseInsensitiveAndFallsBack() {
        assertEquals(TraderMode.ALL, TraderMode.parse("all"));
        assertEquals(TraderMode.MOB_WHITELIST, TraderMode.parse("  Mob_Whitelist "));
        assertEquals(TraderMode.ONLY_VILLAGER, TraderMode.parse(null));
        assertEquals(TraderMode.ONLY_VILLAGER, TraderMode.parse("garbage"));
    }

    @Test
    void onlyVillagerAllowsVillagerOnly() {
        assertTrue(TraderMode.ONLY_VILLAGER.allows("minecraft:villager", List.of(), List.of()));
        assertFalse(TraderMode.ONLY_VILLAGER.allows("minecraft:zombie", List.of(), List.of()));
    }

    @Test
    void allAllowsAnyType() {
        assertTrue(TraderMode.ALL.allows("minecraft:zombie", List.of(), List.of()));
        assertTrue(TraderMode.ALL.allows("somemod:robot", List.of(), List.of()));
    }

    @Test
    void whitelistAllowsListedOnly() {
        List<String> wl = List.of("minecraft:zombie", "skeleton");
        assertTrue(TraderMode.MOB_WHITELIST.allows("minecraft:zombie", wl, List.of()));
        assertTrue(TraderMode.MOB_WHITELIST.allows("minecraft:skeleton", wl, List.of()), "bare id matches");
        assertFalse(TraderMode.MOB_WHITELIST.allows("minecraft:creeper", wl, List.of()));
        assertFalse(TraderMode.MOB_WHITELIST.allows("minecraft:zombie", List.of(), List.of()),
                "empty whitelist allows nothing");
    }

    @Test
    void blacklistRejectsListedOnly() {
        List<String> bl = List.of("minecraft:creeper");
        assertFalse(TraderMode.MOB_BLACKLIST.allows("minecraft:creeper", List.of(), bl));
        assertTrue(TraderMode.MOB_BLACKLIST.allows("minecraft:villager", List.of(), bl));
        assertTrue(TraderMode.MOB_BLACKLIST.allows("minecraft:zombie", List.of(), List.of()),
                "empty blacklist allows all");
    }

    @Test
    void normalizationHandlesCaseAndNamespace() {
        assertTrue(TraderMode.MOB_WHITELIST.allows("Zombie", List.of("MINECRAFT:ZOMBIE"), List.of()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "it.pruefert.headvault.access.villager.TraderModeTest"`
Expected: FAIL — compilation error, `TraderMode` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/it/pruefert/headvault/access/villager/TraderMode.java`:

```java
package it.pruefert.headvault.access.villager;

import java.util.List;
import java.util.Locale;

/**
 * Decides which entity types may act as a named HeadVault trader. Pure — no Minecraft types — so
 * the mode logic stays unit-testable. Entity ids are normalized (trimmed, lower-cased, default
 * {@code minecraft:} namespace) before comparison.
 */
public enum TraderMode {
    ONLY_VILLAGER,
    ALL,
    MOB_WHITELIST,
    MOB_BLACKLIST;

    private static final String VILLAGER_ID = "minecraft:villager";

    /** Case-insensitive parse; null or unknown values fall back to {@link #ONLY_VILLAGER}. */
    public static TraderMode parse(String raw) {
        if (raw == null) {
            return ONLY_VILLAGER;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ONLY_VILLAGER;
        }
    }

    /** True if an entity of the given type id may be a named trader under this mode. */
    public boolean allows(String entityTypeId, List<String> whitelist, List<String> blacklist) {
        String id = normalize(entityTypeId);
        if (id == null) {
            return false;
        }
        return switch (this) {
            case ONLY_VILLAGER -> VILLAGER_ID.equals(id);
            case ALL -> true;
            case MOB_WHITELIST -> contains(whitelist, id);
            case MOB_BLACKLIST -> !contains(blacklist, id);
        };
    }

    private static boolean contains(List<String> ids, String normalizedId) {
        if (ids == null) {
            return false;
        }
        for (String entry : ids) {
            if (normalizedId.equals(normalize(entry))) {
                return true;
            }
        }
        return false;
    }

    /** Trim, lower-case, and prepend the default {@code minecraft:} namespace if none is present. */
    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        return s.contains(":") ? s : "minecraft:" + s;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "it.pruefert.headvault.access.villager.TraderModeTest"`
Expected: PASS — all 6 tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/it/pruefert/headvault/access/villager/TraderMode.java src/test/java/it/pruefert/headvault/access/villager/TraderModeTest.java
git commit -m "feat: add TraderMode enum for entity-type eligibility"
```

---

### Task 2: Config fields on `access.villager`

**Files:**
- Modify: `src/main/java/it/pruefert/headvault/config/HeadVaultConfig.java` (imports + `Access.Villager` class, lines ~8-12 and ~93-97)
- Test: `src/test/java/it/pruefert/headvault/config/ConfigTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: public fields `String HeadVaultConfig.Access.Villager.mode`, `List<String> entityWhitelist`, `List<String> entityBlacklist`.

- [ ] **Step 1: Write the failing test**

Add these two tests to `src/test/java/it/pruefert/headvault/config/ConfigTest.java` (inside the class, after the existing tests):

```java
    @Test
    void villagerTraderDefaultsToOnlyVillager() {
        HeadVaultConfig config = new ConfigManager(dir, log).loadOrCreate();
        assertEquals("ONLY_VILLAGER", config.access.villager.mode);
        assertTrue(config.access.villager.entityWhitelist.isEmpty());
        assertTrue(config.access.villager.entityBlacklist.isEmpty());
    }

    @Test
    void parsesTraderModeAndEntityLists() throws IOException {
        String json = """
                {
                  "access": {
                    "villager": {
                      "enabled": true,
                      "name": "Head Trader",
                      "mode": "MOB_WHITELIST",
                      "entityWhitelist": ["minecraft:zombie", "minecraft:skeleton"],
                      "entityBlacklist": ["minecraft:creeper"]
                    }
                  }
                }
                """;
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("config.json"), json, StandardCharsets.UTF_8);

        HeadVaultConfig config = new ConfigManager(dir, log).loadOrCreate();
        assertEquals("MOB_WHITELIST", config.access.villager.mode);
        assertEquals(2, config.access.villager.entityWhitelist.size());
        assertTrue(config.access.villager.entityWhitelist.contains("minecraft:zombie"));
        assertEquals(1, config.access.villager.entityBlacklist.size());
        assertTrue(config.access.villager.entityBlacklist.contains("minecraft:creeper"));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "it.pruefert.headvault.config.ConfigTest"`
Expected: FAIL — compilation error, `mode` / `entityWhitelist` / `entityBlacklist` do not exist on `Villager`.

- [ ] **Step 3: Write minimal implementation**

In `HeadVaultConfig.java`, add two imports next to the existing `java.util` imports (after `import java.util.EnumMap;`):

```java
import java.util.ArrayList;
import java.util.List;
```

Then replace the `Villager` static class (currently lines ~93-97):

```java
        public static final class Villager {
            public boolean enabled = true;
            public String name = "Head Trader";
            public boolean caseInsensitive = true;
        }
```

with:

```java
        public static final class Villager {
            public boolean enabled = true;
            public String name = "Head Trader";
            public boolean caseInsensitive = true;
            /** Which mob types may be a named trader: ONLY_VILLAGER | ALL | MOB_WHITELIST | MOB_BLACKLIST. */
            public String mode = "ONLY_VILLAGER";
            /** Entity type ids allowed when mode = MOB_WHITELIST, e.g. ["minecraft:zombie"]. */
            public List<String> entityWhitelist = new ArrayList<>();
            /** Entity type ids blocked when mode = MOB_BLACKLIST. */
            public List<String> entityBlacklist = new ArrayList<>();
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "it.pruefert.headvault.config.ConfigTest"`
Expected: PASS — all ConfigTest tests green, including the two new ones.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/it/pruefert/headvault/config/HeadVaultConfig.java src/test/java/it/pruefert/headvault/config/ConfigTest.java
git commit -m "feat: add trader mode + entity lists to villager config"
```

---

### Task 3: `McEntities` helper + `NamedVillagerListener` rewrite

**Files:**
- Create: `src/main/java/it/pruefert/headvault/compat/McEntities.java`
- Modify: `src/main/java/it/pruefert/headvault/access/villager/NamedVillagerListener.java` (full rewrite)

**Interfaces:**
- Consumes: `TraderMode.parse` / `TraderMode.allows` (Task 1); `Access.Villager.mode/entityWhitelist/entityBlacklist` (Task 2).
- Produces: `static String McEntities.typeId(Entity entity)`; unchanged `static boolean NamedVillagerListener.matches(Entity, HeadVaultConfig)`.

> No new automated test: both files are thin Minecraft glue (registry lookup + name match already covered conceptually by Task 1). Verification is a full compile + the existing suite. The name-match branch is unchanged from the current implementation.

- [ ] **Step 1: Create the compat helper**

Create `src/main/java/it/pruefert/headvault/compat/McEntities.java`:

```java
package it.pruefert.headvault.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/**
 * Version-sensitive entity-registry lookups. Part of the {@code compat} package — the only place
 * that should need editing when Minecraft moves registry/identifier APIs.
 */
public final class McEntities {

    private McEntities() {
    }

    /** The entity's registered type id, e.g. {@code "minecraft:villager"}. */
    public static String typeId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }
}
```

- [ ] **Step 2: Rewrite the listener**

Replace the entire contents of `src/main/java/it/pruefert/headvault/access/villager/NamedVillagerListener.java`:

```java
package it.pruefert.headvault.access.villager;

import it.pruefert.headvault.compat.McEntities;
import it.pruefert.headvault.config.HeadVaultConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * The "named trader" access mode: any entity whose custom name matches the configured string opens
 * the shop for everyone on right-click (no permission needed). Which entity types qualify is
 * controlled by {@code access.villager.mode} (default ONLY_VILLAGER — vanilla villagers only). The
 * interaction handler cancels the vanilla GUI when this matches.
 */
public final class NamedVillagerListener {

    private NamedVillagerListener() {
    }

    public static boolean matches(Entity entity, HeadVaultConfig config) {
        HeadVaultConfig.Access.Villager v = config.access.villager;

        // 1. Name match first — cheap, rejects the common case before any registry lookup.
        Component customName = entity.getCustomName();
        if (customName == null) {
            return false;
        }
        if (v.name == null || v.name.isBlank()) {
            return false;
        }
        String actual = customName.getString();
        boolean nameOk = v.caseInsensitive ? actual.equalsIgnoreCase(v.name) : actual.equals(v.name);
        if (!nameOk) {
            return false;
        }

        // 2. Entity-type eligibility per configured mode.
        return TraderMode.parse(v.mode)
                .allows(McEntities.typeId(entity), v.entityWhitelist, v.entityBlacklist);
    }
}
```

Note: the `net.minecraft.world.entity.npc.villager.Villager` import is intentionally gone.

- [ ] **Step 3: Verify it compiles and existing tests pass**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — compiles, all existing tests (CatalogStore, HeadJson, HeadStacks, Config, EconomyMath, TraderMode) pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/it/pruefert/headvault/compat/McEntities.java src/main/java/it/pruefert/headvault/access/villager/NamedVillagerListener.java
git commit -m "feat: gate named trader by configurable entity-type mode"
```

---

### Task 4: README documentation

**Files:**
- Modify: `README.md` (access-modes table ~line 51, config JSON block ~line 129, field reference table ~line 156)

**Interfaces:** none (docs only).

- [ ] **Step 1: Update the Access-modes table row**

Replace the `Named villager` row (currently line 51):

```markdown
| **Named villager** | Name any villager your configured name (default **“Head Trader”**) and right-clicking it opens the shop for **everyone** — no permission needed. Hold a name tag to rename it back to normal. |
```

with:

```markdown
| **Named villager** | Name a villager (or, via `access.villager.mode`, any mob) your configured name (default **“Head Trader”**) and right-clicking opens the shop for **everyone** — no permission needed. Hold a name tag to rename it back to normal. |
```

- [ ] **Step 2: Update the config JSON block**

Replace the `"villager"` line in the `access` block (currently line 129):

```markdown
    "villager": { "enabled": true, "name": "Head Trader", "caseInsensitive": true }
```

with:

```markdown
    "villager": {
      "enabled": true,
      "name": "Head Trader",
      "caseInsensitive": true,
      "mode": "ONLY_VILLAGER",        // which mobs can be a named trader: "ONLY_VILLAGER" | "ALL" | "MOB_WHITELIST" | "MOB_BLACKLIST"
      "entityWhitelist": [],          // entity type ids allowed when mode = "MOB_WHITELIST", e.g. ["minecraft:zombie"]
      "entityBlacklist": []           // entity type ids blocked when mode = "MOB_BLACKLIST"
    }
```

- [ ] **Step 3: Update the field reference table**

Replace the `access.villager` row (currently line 156):

```markdown
| `access.villager.enabled` / `name` / `caseInsensitive` | `true` / `Head Trader` / `true` | Toggle named-villager mode and the trigger name. |
```

with these three rows:

```markdown
| `access.villager.enabled` / `name` / `caseInsensitive` | `true` / `Head Trader` / `true` | Toggle named-trader mode, the trigger name, and case-sensitivity of the name match. |
| `access.villager.mode` | `"ONLY_VILLAGER"` | Which entity types may be a named trader: `ONLY_VILLAGER` (villagers only — default, unchanged behavior), `ALL` (any mob), `MOB_WHITELIST` (only ids in `entityWhitelist`), `MOB_BLACKLIST` (any mob except ids in `entityBlacklist`). |
| `access.villager.entityWhitelist` / `entityBlacklist` | `[]` / `[]` | Entity type ids (e.g. `"minecraft:zombie"`; a bare `"zombie"` assumes the `minecraft` namespace) used by the `MOB_WHITELIST` / `MOB_BLACKLIST` modes. |
```

- [ ] **Step 4: Verify the JSONC block is still valid-ish**

Read back the edited `## ⚙️ Configuration` section and confirm: brackets balance, the `villager` object has a trailing `}` and the `access` block closes correctly.

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: document configurable trader entity-type mode"
```

---

## Post-Implementation

- [ ] Run full build once more: `./gradlew build` → BUILD SUCCESSFUL.
- [ ] Deliver the updated **Modrinth description** text in chat (no repo file — `mc-publish` does not sync the project body).
- [ ] (Branch already `feat/allow-all-traders`.) Push when the user asks; invoke `superpowers:finishing-a-development-branch` for merge/PR options.

## Self-Review Notes

- **Spec coverage:** mode enum (Task 1) ✓; config fields (Task 2) ✓; compat helper + listener (Task 3) ✓; README Config + Access-modes (Task 4) ✓; Modrinth text (Post-Implementation) ✓; tests (Task 1, Task 2) ✓.
- **Placeholder scan:** no TBD/TODO; all code blocks complete.
- **Type consistency:** `TraderMode.parse(String)` and `allows(String, List<String>, List<String>)` used identically in Task 1 and Task 3; config field names `mode`/`entityWhitelist`/`entityBlacklist` consistent across Tasks 2–4.
