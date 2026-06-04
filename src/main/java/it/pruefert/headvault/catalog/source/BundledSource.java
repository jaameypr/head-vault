package it.pruefert.headvault.catalog.source;

import it.pruefert.headvault.catalog.Head;
import it.pruefert.headvault.catalog.HeadCategory;
import it.pruefert.headvault.catalog.HeadJson;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reads the catalog snapshot embedded in the mod jar at {@code /cache/<slug>.json}. This is the
 * ultimate offline fallback: it guarantees the shop works on first launch and whenever the live API
 * is unreachable, with zero network access. Pure JDK + classpath — no Minecraft types.
 */
public final class BundledSource implements HeadSource {

    private final ClassLoader classLoader;

    public BundledSource() {
        this(BundledSource.class.getClassLoader());
    }

    public BundledSource(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public String id() {
        return "bundled";
    }

    @Override
    public List<Head> fetch(HeadCategory category) throws Exception {
        return HeadJson.parseArray(readResource("cache/" + category.slug() + ".json"));
    }

    /** True if a bundled snapshot exists for the category. */
    public boolean has(HeadCategory category) {
        return classLoader.getResource("cache/" + category.slug() + ".json") != null;
    }

    private String readResource(String path) throws IOException {
        try (InputStream in = classLoader.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Bundled resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
