package it.pruefert.headvault.catalog.refresh;

import it.pruefert.headvault.catalog.CatalogStore;
import it.pruefert.headvault.catalog.Head;
import it.pruefert.headvault.catalog.HeadCatalog;
import it.pruefert.headvault.catalog.HeadCategory;
import it.pruefert.headvault.catalog.HeadJson;
import it.pruefert.headvault.catalog.source.HeadSource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Owns all catalog refreshing. Runs entirely off the server thread on a small daemon pool:
 * <ol>
 *   <li>On {@link #start()} it immediately publishes whatever is on disk / bundled, so the shop
 *       works instantly, then triggers a network refresh only if the cache is stale (TTL).</li>
 *   <li>Each category is fetched in parallel with a per-task timeout; a failure keeps the previous
 *       cache file for that category (never blanks it).</li>
 *   <li>After a refresh, a fresh {@link HeadCatalog} is built and handed to {@code onCatalogReady},
 *       which atomically swaps the reference the server reads.</li>
 * </ol>
 * Pure (no Minecraft types); the clock is injected so refresh/TTL logic is testable.
 */
public final class CatalogRefreshService implements AutoCloseable {

    private final HeadSource source;
    private final CatalogStore store;
    private final Duration refreshInterval;
    private final Duration perCategoryTimeout;
    private final Consumer<HeadCatalog> onCatalogReady;
    private final LongSupplier clock;
    private final org.slf4j.Logger log;

    private final ScheduledExecutorService scheduler;
    private final java.util.concurrent.ExecutorService workers;

    public CatalogRefreshService(HeadSource source,
                                 CatalogStore store,
                                 Duration refreshInterval,
                                 Duration perCategoryTimeout,
                                 Consumer<HeadCatalog> onCatalogReady,
                                 LongSupplier clock,
                                 org.slf4j.Logger log) {
        this.source = source;
        this.store = store;
        this.refreshInterval = refreshInterval;
        this.perCategoryTimeout = perCategoryTimeout;
        this.onCatalogReady = onCatalogReady;
        this.clock = clock;
        this.log = log;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemon("HeadVault-catalog-scheduler"));
        this.workers = Executors.newFixedThreadPool(
                Math.min(HeadCategory.values().length, 6), daemon("HeadVault-catalog-worker"));
    }

    /** Publish the cached catalog now, refresh if stale, and schedule periodic refreshes. */
    public void start() {
        publishFromCache();
        if (!store.isFresh(refreshInterval, clock.getAsLong())) {
            refreshNow();
        } else {
            log.info("[HeadVault] Catalog cache is fresh; skipping startup refresh");
        }
        long minutes = Math.max(1, refreshInterval.toMinutes());
        scheduler.scheduleAtFixedRate(this::refreshNow, minutes, minutes, TimeUnit.MINUTES);
    }

    /** Rebuild from disk/bundled and publish (no network). */
    public void publishFromCache() {
        HeadCatalog catalog = store.loadAll();
        onCatalogReady.accept(catalog);
        log.info("[HeadVault] Loaded {} heads from cache ({} categories)",
                catalog.total(), catalog.populatedCategories().size());
    }

    /**
     * Fetch every category in parallel from {@link #source}, write successes to the cache, then
     * rebuild and publish. Runs on the scheduler thread; network work runs on the worker pool.
     * Returns a future that completes when the refresh (and publish) is done.
     */
    public CompletableFuture<Void> refreshNow() {
        log.info("[HeadVault] Refreshing head catalog from source '{}'", source.id());
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        CompletableFuture<?>[] futures = new CompletableFuture<?>[HeadCategory.values().length];
        HeadCategory[] categories = HeadCategory.values();
        for (int i = 0; i < categories.length; i++) {
            HeadCategory category = categories[i];
            futures[i] = CompletableFuture
                    .supplyAsync(() -> fetchAndStore(category), workers)
                    .orTimeout(perCategoryTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .handle((success, error) -> {
                        if (Boolean.TRUE.equals(success)) {
                            ok.incrementAndGet();
                        } else {
                            failed.incrementAndGet();
                            if (error != null) {
                                log.warn("[HeadVault] Category '{}' refresh failed: {}",
                                        category.slug(), rootMessage(error));
                            }
                        }
                        return null;
                    });
        }

        return CompletableFuture.allOf(futures).whenComplete((v, t) -> {
            if (ok.get() > 0) {
                try {
                    store.markRefreshed(clock.getAsLong());
                } catch (Exception e) {
                    log.warn("[HeadVault] Could not write refresh stamp: {}", e.toString());
                }
            }
            log.info("[HeadVault] Catalog refresh complete: {} ok, {} failed (kept cache)",
                    ok.get(), failed.get());
            publishFromCache();
        });
    }

    /** @return true if the category was fetched and written; false to keep the existing cache. */
    private boolean fetchAndStore(HeadCategory category) {
        try {
            List<Head> heads = source.fetch(category);
            if (heads.isEmpty()) {
                return false;
            }
            store.writeCategory(category, HeadJson.toJson(heads));
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        workers.shutdownNow();
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }

    private static ThreadFactory daemon(String name) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, name + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
