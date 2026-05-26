package com.halilov.online.notification;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.common.InMemoryThrottle;

import java.time.Duration;

@RestController
@RequestMapping("/api/products")
public class StockNotificationController {

    // Per-IP cap: stops a single source from mass-subscribing a victim email
    // to hundreds of products (each restock would then fan out emails).
    private static final int PER_IP_MAX = 10;
    private static final Duration PER_IP_WINDOW = Duration.ofHours(1);

    // Per-email cap: even across IPs, the same address can't be enrolled to
    // more than this many products per hour. Bounds the spam any one victim
    // could receive when stock returns.
    private static final int PER_EMAIL_MAX = 5;
    private static final Duration PER_EMAIL_WINDOW = Duration.ofHours(1);

    private final StockNotificationService service;
    private final InMemoryThrottle throttle;

    public StockNotificationController(StockNotificationService service, InMemoryThrottle throttle) {
        this.service = service;
        this.throttle = throttle;
    }

    @PostMapping("/{id}/stock-notify")
    public ResponseEntity<Void> subscribe(
        @PathVariable Long id,
        @RequestBody StockNotificationDtos.SubscribeRequest req,
        HttpServletRequest http
    ) {
        String ip = http.getRemoteAddr();
        if (!throttle.tryAcquire("stock-notify:ip", ip, PER_IP_MAX, PER_IP_WINDOW)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate limited");
        }
        String email = req == null ? null : req.email();
        if (email != null && !email.isBlank()) {
            String emailKey = email.trim().toLowerCase();
            if (!throttle.tryAcquire("stock-notify:email", emailKey, PER_EMAIL_MAX, PER_EMAIL_WINDOW)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate limited");
            }
        }
        service.subscribe(id, email);
        return ResponseEntity.accepted().build();
    }
}
