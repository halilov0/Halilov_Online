package com.halilov.online.order;

import java.util.List;

/**
 * Records returned by {@link DeliveryController} — the quote shape
 * (option + resolved price) plus the raw config the SPA mirrors.
 */
public class DeliveryDtos {

    public record Option(
        DeliveryMethod method,
        String label,
        String description,
        int priceAgorot,
        int basePriceAgorot,
        int freeAboveAgorot
    ) {}

    public record Quote(List<Option> options) {}

    public record Config(int courierFlatAgorot, int freeAboveAgorot) {}
}
