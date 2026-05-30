package com.halilov.online.common;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Uniform response for bulk admin operations.
 *
 * @param affected how many rows the operation actually changed/removed
 * @param skipped  requested ids that were a no-op (already gone, or
 *                 protected — e.g. an admin row skipped by a bulk
 *                 enable/disable)
 */
public record BulkResult(int affected, int skipped) {

    /** Cap on how many ids we serialise into an audit row's metadata, so a
     *  2000-id sweep doesn't bloat the log. Beyond this we record a marker. */
    private static final int MAX_METADATA_IDS = 200;

    /**
     * Compact JSON snapshot of the affected ids for the audit trail, e.g.
     * {@code {"ids":[1,2,3]}}. Truncates past {@link #MAX_METADATA_IDS} so
     * the {@code audit_log.metadata} column stays bounded.
     */
    public static String idsMetadata(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return "{\"ids\":[]}";
        List<Long> capped = ids.size() > MAX_METADATA_IDS ? ids.subList(0, MAX_METADATA_IDS) : ids;
        String joined = capped.stream().map(String::valueOf).collect(Collectors.joining(","));
        if (ids.size() > MAX_METADATA_IDS) {
            return "{\"ids\":[" + joined + "],\"truncated\":" + (ids.size() - MAX_METADATA_IDS) + "}";
        }
        return "{\"ids\":[" + joined + "]}";
    }
}
