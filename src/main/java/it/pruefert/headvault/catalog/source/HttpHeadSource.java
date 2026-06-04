package it.pruefert.headvault.catalog.source;

import it.pruefert.headvault.catalog.Head;
import it.pruefert.headvault.catalog.HeadCategory;
import it.pruefert.headvault.catalog.HeadJson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Base for HTTP-backed head sources. Adds connect/read timeouts, a bounded retry loop with
 * backoff, and a configurable User-Agent (minecraft-heads.com asks integrators to identify
 * themselves and to buffer requests rather than hammer the API). Pure JDK — no Minecraft types.
 */
abstract class HttpHeadSource implements HeadSource {

    private final HttpClient client;
    private final Duration requestTimeout;
    private final int maxRetries;
    private final String userAgent;

    protected HttpHeadSource(Duration requestTimeout, int maxRetries, String userAgent) {
        this.requestTimeout = requestTimeout;
        this.maxRetries = Math.max(0, maxRetries);
        this.userAgent = userAgent;
        this.client = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Build the request URL for a category. */
    protected abstract String buildUrl(HeadCategory category);

    /** Parse a response body into heads. Default expects a bare JSON array; v2 overrides. */
    protected List<Head> parse(String body) {
        return HeadJson.parseArray(body);
    }

    @Override
    public List<Head> fetch(HeadCategory category) throws Exception {
        return parse(httpGet(buildUrl(category)));
    }

    private String httpGet(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .GET()
                .build();

        IOException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status / 100 == 2) {
                    return response.body();
                }
                last = new IOException("HTTP " + status + " from " + url);
            } catch (IOException e) {
                last = e;
            }
            if (attempt < maxRetries) {
                Thread.sleep(500L * (attempt + 1));
            }
        }
        throw last != null ? last : new IOException("Request failed: " + url);
    }
}
