package it.pruefert.headvault.catalog.source;

import it.pruefert.headvault.catalog.Head;
import it.pruefert.headvault.catalog.HeadCategory;

import java.util.List;

/**
 * A provider of head records for a single category. Implementations must be thread-safe and
 * time-bounded (the refresh service fetches categories in parallel off the server thread).
 *
 * <p>This interface is the seam that makes the catalog source pluggable: {@code v1}, {@code v2},
 * and {@code bundled} all implement it, selected by config.
 */
public interface HeadSource {

    /** Short identifier for logging (e.g. {@code "v1"}, {@code "v2"}, {@code "bundled"}). */
    String id();

    /**
     * Fetch all heads for the given category.
     *
     * @throws Exception on network/parse failure; the caller decides whether to fall back to cache
     */
    List<Head> fetch(HeadCategory category) throws Exception;
}
