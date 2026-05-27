package com.halilov.online.order;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public delivery pricing — lets the SPA quote shipping without
 * creating an order first.
 *
 * <p>{@code GET /quote?subtotalAgorot=...} returns the available
 * delivery options and the resolved price (including the
 * "free above {@code N}" cutoff). {@code GET /config} exposes the raw
 * configuration so the cart can mirror it client-side — the backend
 * stays the single source of truth for delivery rules.
 */
@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping("/quote")
    public DeliveryDtos.Quote quote(@RequestParam(name = "subtotalAgorot", defaultValue = "0") int subtotalAgorot) {
        return deliveryService.quote(Math.max(0, subtotalAgorot));
    }

    @GetMapping("/config")
    public DeliveryDtos.Config config() {
        return deliveryService.config();
    }
}
