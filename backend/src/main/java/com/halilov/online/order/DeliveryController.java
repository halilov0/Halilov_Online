package com.halilov.online.order;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
