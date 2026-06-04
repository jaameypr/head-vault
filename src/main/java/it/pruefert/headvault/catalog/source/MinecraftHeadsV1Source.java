package it.pruefert.headvault.catalog.source;

import it.pruefert.headvault.catalog.HeadCategory;

import java.time.Duration;

/**
 * The legacy minecraft-heads.com v1 endpoint: {@code GET /scripts/api.php?cat=<slug>&tags=true}.
 * No authentication. NOTE: v1 is officially deprecated and was slated for shutdown around the end
 * of 2025 — it may be unavailable, in which case the refresh service falls back to the on-disk /
 * bundled cache.
 */
public final class MinecraftHeadsV1Source extends HttpHeadSource {

    private static final String URL_FORMAT = "https://minecraft-heads.com/scripts/api.php?cat=%s&tags=true";

    public MinecraftHeadsV1Source(Duration requestTimeout, int maxRetries, String userAgent) {
        super(requestTimeout, maxRetries, userAgent);
    }

    @Override
    public String id() {
        return "v1";
    }

    @Override
    protected String buildUrl(HeadCategory category) {
        return String.format(URL_FORMAT, category.slug());
    }
}
