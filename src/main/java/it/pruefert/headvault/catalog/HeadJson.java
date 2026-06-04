package it.pruefert.headvault.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parser for the minecraft-heads.com head-array JSON (shared by the v1 and v2 sources and by the
 * bundled fallback). A record is {@code {name, uuid, value, tags}} where {@code tags} is an
 * optional comma-separated string. Pure — no Minecraft types.
 */
public final class HeadJson {

    private HeadJson() {
    }

    /**
     * Parse a JSON array of head objects. Individual malformed entries are skipped so one bad
     * record cannot wipe a whole category; a structurally invalid document throws.
     *
     * @throws IllegalArgumentException if the document is not a JSON array
     */
    public static List<Head> parseArray(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray()) {
            throw new IllegalArgumentException("Expected a JSON array of heads");
        }
        return fromArray(root.getAsJsonArray());
    }

    /**
     * Like {@link #parseArray} but tolerant of a wrapper object — the v2 API may return the head
     * list nested under a field such as {@code data}/{@code heads}/{@code results}. If the root is
     * already an array it is used directly; otherwise the first array-valued member (preferring the
     * common wrapper keys) is parsed.
     */
    public static List<Head> parseArrayOrWrapped(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (root.isJsonArray()) {
            return parseArray(json);
        }
        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            for (String key : new String[] {"data", "heads", "results", "result", "items"}) {
                JsonElement el = obj.get(key);
                if (el != null && el.isJsonArray()) {
                    return fromArray(el.getAsJsonArray());
                }
            }
            for (JsonElement el : obj.asMap().values()) {
                if (el.isJsonArray()) {
                    return fromArray(el.getAsJsonArray());
                }
            }
        }
        throw new IllegalArgumentException("No head array found in response");
    }

    private static List<Head> fromArray(JsonArray array) {
        List<Head> heads = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            Head head = parseRecord(element);
            if (head != null) {
                heads.add(head);
            }
        }
        return heads;
    }

    /** Parse a single record; returns null if it is unusable (no/invalid UUID or no value). */
    private static Head parseRecord(JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        String value = optString(obj, "value");
        String uuidStr = optString(obj, "uuid");
        if (value == null || value.isBlank() || uuidStr == null || uuidStr.isBlank()) {
            return null;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        String name = optString(obj, "name");
        return new Head(name == null ? "" : name, uuid, value, parseTags(optString(obj, "tags")));
    }

    /**
     * Serialize heads to the canonical on-disk array shape ({@code tags} as a comma-separated
     * string), which {@link #parseArray} reads back. Used to normalize every source (v1/v2/bundled)
     * into one cache format.
     */
    public static String toJson(List<Head> heads) {
        JsonArray array = new JsonArray(heads.size());
        for (Head head : heads) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", head.name());
            obj.addProperty("uuid", head.uuid().toString());
            obj.addProperty("value", head.value());
            obj.addProperty("tags", String.join(",", head.tags()));
            array.add(obj);
        }
        return array.toString();
    }

    /** Tags arrive as a single comma-separated string; split, trim, drop blanks. */
    static List<String> parseTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return tags;
    }

    private static String optString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }
}
