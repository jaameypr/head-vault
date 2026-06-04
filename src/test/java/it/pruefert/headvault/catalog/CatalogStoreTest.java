package it.pruefert.headvault.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogStoreTest {

    private static final String VALID = """
            [{"name":"X","uuid":"10c188cd-028f-49a5-9e4b-ed62ceac140c","value":"v","tags":"a,b"}]
            """;

    @TempDir
    Path dir;

    @Test
    void ttlGatesRefresh() throws IOException {
        CatalogStore store = new CatalogStore(dir);
        long now = 1_000_000L;
        store.markRefreshed(now);
        assertTrue(store.isFresh(Duration.ofHours(24), now + 1000), "just refreshed -> fresh");
        assertFalse(store.isFresh(Duration.ofMillis(500), now + 1000), "older than ttl -> stale");
        assertFalse(new CatalogStore(dir.resolve("empty")).isFresh(Duration.ofHours(1), now), "never refreshed -> stale");
    }

    @Test
    void offlineFallbackToBundledWhenFileMissing() {
        // No cache file written; loadCategory must fall back to the bundled snapshot on the classpath.
        CatalogStore store = new CatalogStore(dir);
        List<Head> heads = store.loadCategory(HeadCategory.BLOCKS);
        assertFalse(heads.isEmpty(), "bundled blocks.json should provide a fallback");
    }

    @Test
    void corruptFileRecoversFromBundled() throws IOException {
        CatalogStore store = new CatalogStore(dir);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("blocks.json"), "this is not json", StandardCharsets.UTF_8);
        List<Head> heads = store.loadCategory(HeadCategory.BLOCKS);
        assertFalse(heads.isEmpty(), "unparseable cache file should fall back to bundled, not load empty");
    }

    @Test
    void writeRejectsInvalidJsonAndKeepsNoFile() {
        CatalogStore store = new CatalogStore(dir);
        assertThrows(RuntimeException.class, () -> store.writeCategory(HeadCategory.ALPHABET, "garbage"));
        assertTrue(store.readCategoryRaw(HeadCategory.ALPHABET).isEmpty(), "invalid write must not create a file");
    }

    @Test
    void writeThenLoadFromDisk() throws IOException {
        CatalogStore store = new CatalogStore(dir);
        store.writeCategory(HeadCategory.ANIMALS, VALID);
        List<Head> heads = store.loadCategory(HeadCategory.ANIMALS);
        assertEquals(1, heads.size());
        assertEquals("X", heads.get(0).name());
    }
}
