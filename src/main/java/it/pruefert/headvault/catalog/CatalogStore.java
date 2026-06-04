package it.pruefert.headvault.catalog;

import it.pruefert.headvault.catalog.source.BundledSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * On-disk cache for the head catalog: one raw JSON file per category under {@code cacheDir}, plus a
 * {@code .last-refresh} stamp. Pure JDK (the {@code cacheDir} is injected) so it is unit-testable
 * without a Minecraft runtime.
 *
 * <p>Reliability properties, each covered by a unit test:
 * <ul>
 *   <li><b>Atomic, validated writes</b> — a category is parsed <i>before</i> being committed and is
 *       written to a temp file then atomically renamed, so a truncated/partial download can never
 *       overwrite a good cache file.</li>
 *   <li><b>Corrupt-file recovery</b> — {@link #loadCategory} falls back to the bundled snapshot when
 *       a cache file is missing <i>or</i> fails to parse (not only when absent).</li>
 *   <li><b>Offline fallback / TTL</b> — {@link #isFresh} gates network refresh; loading never
 *       requires the network.</li>
 * </ul>
 */
public final class CatalogStore {

    private static final String STAMP_FILE = ".last-refresh";

    private final Path cacheDir;
    private final BundledSource bundled;

    public CatalogStore(Path cacheDir) {
        this(cacheDir, new BundledSource());
    }

    public CatalogStore(Path cacheDir, BundledSource bundled) {
        this.cacheDir = cacheDir;
        this.bundled = bundled;
    }

    public Path cacheDir() {
        return cacheDir;
    }

    /** Validate, then atomically write the raw JSON for a category. Rejects unparseable bodies. */
    public void writeCategory(HeadCategory category, String rawJson) throws IOException {
        // Validate before committing — throws if the body is not a usable head array.
        HeadJson.parseArray(rawJson);

        Files.createDirectories(cacheDir);
        Path target = categoryFile(category);
        Path tmp = cacheDir.resolve(category.slug() + ".json.tmp");
        Files.writeString(tmp, rawJson, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Optional<String> readCategoryRaw(HeadCategory category) {
        Path file = categoryFile(category);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Load one category: disk first; on missing or unparseable file, fall back to the bundled snapshot. */
    public List<Head> loadCategory(HeadCategory category) {
        Optional<String> raw = readCategoryRaw(category);
        if (raw.isPresent()) {
            try {
                return HeadJson.parseArray(raw.get());
            } catch (RuntimeException parseError) {
                // fall through to bundled
            }
        }
        try {
            return bundled.fetch(category);
        } catch (Exception bundledError) {
            return List.of();
        }
    }

    /** Build a full catalog snapshot from disk-or-bundled data for every category. */
    public HeadCatalog loadAll() {
        Map<HeadCategory, List<Head>> map = new EnumMap<>(HeadCategory.class);
        for (HeadCategory category : HeadCategory.values()) {
            List<Head> heads = loadCategory(category);
            if (!heads.isEmpty()) {
                map.put(category, heads);
            }
        }
        return HeadCatalog.of(map);
    }

    // ── refresh stamp / TTL ─────────────────────────────────────────────────

    public void markRefreshed(long nowEpochMillis) throws IOException {
        Files.createDirectories(cacheDir);
        Files.writeString(cacheDir.resolve(STAMP_FILE), Long.toString(nowEpochMillis), StandardCharsets.UTF_8);
    }

    /** Epoch millis of the last successful refresh, or -1 if never. */
    public long lastRefresh() {
        Path stamp = cacheDir.resolve(STAMP_FILE);
        if (!Files.isRegularFile(stamp)) {
            return -1L;
        }
        try {
            return Long.parseLong(Files.readString(stamp, StandardCharsets.UTF_8).trim());
        } catch (IOException | NumberFormatException e) {
            return -1L;
        }
    }

    /** True if the cache was refreshed within {@code ttl} of {@code nowEpochMillis}. */
    public boolean isFresh(Duration ttl, long nowEpochMillis) {
        long last = lastRefresh();
        return last >= 0 && (nowEpochMillis - last) < ttl.toMillis();
    }

    private Path categoryFile(HeadCategory category) {
        return cacheDir.resolve(category.slug() + ".json");
    }
}
