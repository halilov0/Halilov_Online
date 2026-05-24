package com.halilov.online.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Computes shipping cost server-side. The checkout UI previews these
 * numbers via {@link #quote}, but {@link #priceFor} is the source of truth
 * — orders never trust client-sent shipping amounts.
 *
 * <p>Today the only method is COURIER (flat rate, free above a threshold).
 * Pickup-point options will arrive together with a real courier API
 * integration; the points come from the courier's network, not from us.
 */
@Service
public class DeliveryService {

    private final int courierFlatAgorot;
    private final int freeAboveAgorot;

    public DeliveryService(
        @Value("${app.delivery.courierFlatAgorot:1990}") int courierFlatAgorot,
        @Value("${app.delivery.freeAboveAgorot:30000}") int freeAboveAgorot
    ) {
        this.courierFlatAgorot = courierFlatAgorot;
        this.freeAboveAgorot = freeAboveAgorot;
    }

    public int priceFor(DeliveryMethod method, int subtotalAgorot) {
        return switch (method) {
            case COURIER -> subtotalAgorot >= freeAboveAgorot ? 0 : courierFlatAgorot;
        };
    }

    public DeliveryDtos.Quote quote(int subtotalAgorot) {
        return new DeliveryDtos.Quote(List.of(
            new DeliveryDtos.Option(
                DeliveryMethod.COURIER,
                "שליח עד הבית",
                "אספקה תוך 3-5 ימי עסקים",
                priceFor(DeliveryMethod.COURIER, subtotalAgorot),
                courierFlatAgorot,
                freeAboveAgorot
            )
        ));
    }

    public DeliveryDtos.Config config() {
        return new DeliveryDtos.Config(courierFlatAgorot, freeAboveAgorot);
    }
}
