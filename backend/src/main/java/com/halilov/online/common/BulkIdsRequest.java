package com.halilov.online.common;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Shared request body for bulk admin operations that act on a set of
 * entity ids (bulk delete, bulk enable/disable). Capped at
 * {@value #MAX_IDS} ids per request so a single call can't sweep an
 * unbounded number of rows.
 */
public record BulkIdsRequest(
    @NotEmpty @Size(max = BulkIdsRequest.MAX_IDS) List<Long> ids
) {
    public static final int MAX_IDS = 2000;
}
