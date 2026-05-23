package com.halilov.online.order;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDtos.OrderView create(Authentication auth, @Valid @RequestBody OrderDtos.CreateOrderRequest req) {
        String email = auth == null ? null : auth.getName();
        return orderService.createOrder(email, req);
    }

    @GetMapping
    public List<OrderDtos.OrderView> mine(Authentication auth) {
        requireAuth(auth);
        return orderService.listMine(auth.getName());
    }

    @GetMapping("/{orderNumber}")
    public OrderDtos.OrderView one(
        Authentication auth,
        @PathVariable String orderNumber,
        @RequestHeader(value = "X-Guest-Token", required = false) String guestToken
    ) {
        if (auth != null && auth.getName() != null) {
            return orderService.getMine(auth.getName(), orderNumber);
        }
        if (guestToken == null || guestToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return orderService.getByToken(orderNumber, guestToken);
    }

    @PostMapping("/{orderNumber}/cancel")
    public OrderDtos.OrderView cancel(
        Authentication auth,
        @PathVariable String orderNumber,
        @Valid @RequestBody(required = false) OrderDtos.CancelRequest req
    ) {
        requireAuth(auth);
        String reason = req == null ? null : req.reason();
        return orderService.customerCancel(auth.getName(), orderNumber, reason);
    }

    private void requireAuth(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
    }
}
