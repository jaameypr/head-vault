package it.pruefert.headvault.catalog;

/**
 * A head together with the category it belongs to. Search spans categories, and per-category
 * pricing needs the origin category, so results carry both. Pure data.
 */
public record CategorizedHead(HeadCategory category, Head head) {
}
