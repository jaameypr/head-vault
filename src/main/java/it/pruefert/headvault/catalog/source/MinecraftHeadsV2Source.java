package it.pruefert.headvault.catalog.source;

import it.pruefert.headvault.catalog.Head;
import it.pruefert.headvault.catalog.HeadCategory;
import it.pruefert.headvault.catalog.HeadJson;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * The current minecraft-heads.com REST API v2. v2 requires a per-request <b>App UUID</b> obtained
 * by registering the application, supports a demo mode for development, and returns the same core
 * fields as v1 ({@code name}, {@code value}, {@code uuid}, {@code tags}).
 *
 * <p><b>The exact v2 path / query-parameter names are intentionally NOT hard-coded.</b> The site
 * blocks automated documentation fetches, so the endpoint is driven entirely by a configurable URL
 * template (see {@code catalog.v2UrlTemplate} in the config). This means correcting the contract to
 * match the official v2 docs is a config edit, not a code change. Placeholders:
 * <ul>
 *   <li>{@code {category}} → the category slug (e.g. {@code blocks})</li>
 *   <li>{@code {appUuid}} → the registered App UUID (URL-encoded; blank in demo mode)</li>
 * </ul>
 * The response is parsed flexibly (bare array or array wrapped under {@code data}/{@code heads}/…).
 */
public final class MinecraftHeadsV2Source extends HttpHeadSource {

    /** Best-effort default; verify against the official v2 docs and override in config if needed. */
    public static final String DEFAULT_URL_TEMPLATE =
            "https://minecraft-heads.com/api/v2/categories/{category}?app={appUuid}&tags=true";

    private final String urlTemplate;
    private final String appUuid;

    public MinecraftHeadsV2Source(Duration requestTimeout, int maxRetries, String userAgent,
                                  String urlTemplate, String appUuid) {
        super(requestTimeout, maxRetries, userAgent);
        this.urlTemplate = (urlTemplate == null || urlTemplate.isBlank()) ? DEFAULT_URL_TEMPLATE : urlTemplate;
        this.appUuid = appUuid == null ? "" : appUuid;
    }

    @Override
    public String id() {
        return "v2";
    }

    @Override
    protected String buildUrl(HeadCategory category) {
        return urlTemplate
                .replace("{category}", encode(category.slug()))
                .replace("{appUuid}", encode(appUuid));
    }

    @Override
    protected List<Head> parse(String body) {
        return HeadJson.parseArrayOrWrapped(body);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
