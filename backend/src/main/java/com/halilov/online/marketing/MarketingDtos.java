package com.halilov.online.marketing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request and response records for the marketing admin endpoints.
 * {@code BroadcastRequest.htmlBody} is admin-typed HTML; jsoup
 * sanitises it inside {@link MarketingService} before persistence.
 */
public class MarketingDtos {

    public record RecipientCount(long eligibleCount) {}

    public record BroadcastRequest(
        @NotBlank @Size(max = 255) String subject,
        @NotBlank @Size(max = 50_000) String htmlBody
    ) {}

    public record BroadcastResult(int queued, long eligibleCount) {}
}
