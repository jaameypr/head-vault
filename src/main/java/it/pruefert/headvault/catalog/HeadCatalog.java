package it.pruefert.headvault.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable in-memory snapshot of the head catalog, indexed by category. The
 * {@link it.pruefert.headvault.catalog.refresh.CatalogRefreshService} builds a new instance
 * off-thread and atomically swaps the reference the server reads, so this type is safely shared
 * across threads. Pure — no Minecraft types.
 */
public final class HeadCatalog {

    private static final HeadCatalog EMPTY = new HeadCatalog(new EnumMap<>(HeadCategory.class));

    private final Map<HeadCategory, List<Head>> byCategory;
    private final Map<String, Head> byId;
    private final int total;

    private HeadCatalog(Map<HeadCategory, List<Head>> byCategory) {
        this.byCategory = byCategory;
        Map<String, Head> idIndex = new java.util.HashMap<>();
        int count = 0;
        for (List<Head> heads : byCategory.values()) {
            count += heads.size();
            for (Head head : heads) {
                idIndex.putIfAbsent(head.id(), head);
            }
        }
        this.byId = idIndex;
        this.total = count;
    }

    public static HeadCatalog empty() {
        return EMPTY;
    }

    public static HeadCatalog of(Map<HeadCategory, List<Head>> source) {
        Map<HeadCategory, List<Head>> copy = new EnumMap<>(HeadCategory.class);
        for (Map.Entry<HeadCategory, List<Head>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new HeadCatalog(copy);
    }

    public List<Head> heads(HeadCategory category) {
        return byCategory.getOrDefault(category, List.of());
    }

    /** Categories that actually have at least one head loaded. */
    public List<HeadCategory> populatedCategories() {
        List<HeadCategory> result = new ArrayList<>();
        for (HeadCategory category : HeadCategory.values()) {
            if (!heads(category).isEmpty()) {
                result.add(category);
            }
        }
        return result;
    }

    public Optional<Head> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public int total() {
        return total;
    }

    public boolean isEmpty() {
        return total == 0;
    }

    /** Case-insensitive substring search across head names and tags, in category order. */
    public List<CategorizedHead> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String lower = query.toLowerCase().trim();
        List<CategorizedHead> results = new ArrayList<>();
        for (HeadCategory category : HeadCategory.values()) {
            for (Head head : heads(category)) {
                if (head.matches(lower)) {
                    results.add(new CategorizedHead(category, head));
                    if (results.size() >= limit) {
                        return Collections.unmodifiableList(results);
                    }
                }
            }
        }
        return Collections.unmodifiableList(results);
    }
}
