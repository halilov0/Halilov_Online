package com.halilov.online.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.order.Order;
import com.halilov.online.order.OrderRepository;
import com.halilov.online.order.OrderService;
import com.halilov.online.order.OrderStatus;
import com.halilov.online.user.User;
import com.halilov.online.user.UserRepository;

/**
 * Payment orchestration. Currently the only provider is "mock" which simulates
 * a hosted-checkout flow against the shop SPA. When a real Grow/Meshulam
 * merchant account is available, swap {@link #buildRedirectUrl} to hit the
 * Grow API and add a separate signed-webhook endpoint to confirm payment.
 */
@Service
public class PaymentService {

    private final OrderRepository orders;
    private final UserRepository users;
    private final OrderService orderService;
    private final String provider;

    public PaymentService(
        OrderRepository orders,
        UserRepository users,
        OrderService orderService,
        @Value("${app.payment.provider:mock}") String provider
    ) {
        this.orders = orders;
        this.users = users;
        this.orderService = orderService;
        this.provider = provider;
    }

    public PaymentDtos.InitiateResponse initiate(String email, String orderNumber) {
        requireProviderEnabled();
        Order order = loadOwnPendingOrder(email, orderNumber);
        return respond(order);
    }

    public PaymentDtos.InitiateResponse initiateGuest(String orderNumber, String token) {
        requireProviderEnabled();
        Order order = loadGuestPendingOrder(orderNumber, token);
        return respond(order);
    }

    public PaymentDtos.InitiateResponse completeMock(String email, String orderNumber, String outcome) {
        requireMockProvider();
        Order order = loadOwnPendingOrder(email, orderNumber);
        return applyMockOutcome(order, outcome);
    }

    public PaymentDtos.InitiateResponse completeMockGuest(String orderNumber, String token, String outcome) {
        requireMockProvider();
        Order order = loadGuestPendingOrder(orderNumber, token);
        return applyMockOutcome(order, outcome);
    }

    /**
     * In prod we default {@code app.payment.provider} to {@code disabled} so
     * the mock-complete endpoint cannot be hit. Hard-stops the "anyone marks
     * their own order PAID" path until a real gateway is wired.
     */
    private void requireProviderEnabled() {
        if ("disabled".equals(provider)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "התשלום אינו זמין כרגע. נציג ייצור איתך קשר להשלמת ההזמנה.");
        }
    }

    private void requireMockProvider() {
        if (!"mock".equals(provider)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "mock complete is disabled");
        }
    }

    private PaymentDtos.InitiateResponse respond(Order order) {
        return new PaymentDtos.InitiateResponse(provider, buildRedirectUrl(order), order.getOrderNumber());
    }

    private PaymentDtos.InitiateResponse applyMockOutcome(Order order, String outcome) {
        if ("success".equalsIgnoreCase(outcome)) {
            orderService.adminUpdateStatus(order.getOrderNumber(), OrderStatus.PAID);
        } else if ("cancel".equalsIgnoreCase(outcome)) {
            orderService.adminUpdateStatus(order.getOrderNumber(), OrderStatus.CANCELLED);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "outcome must be success|cancel");
        }
        return new PaymentDtos.InitiateResponse(provider, "/confirmation/" + order.getOrderNumber(), order.getOrderNumber());
    }

    private Order loadOwnPendingOrder(String email, String orderNumber) {
        User user = users.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no user"));
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        if (!user.getId().equals(order.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
        }
        requirePending(order);
        return order;
    }

    private Order loadGuestPendingOrder(String orderNumber, String token) {
        Order order = orderService.loadByGuestToken(orderNumber, token);
        requirePending(order);
        return order;
    }

    private void requirePending(Order order) {
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "order not in PENDING state");
        }
    }

    private String buildRedirectUrl(Order order) {
        // For real Grow: call Grow's "create payment process" API, return the
        // hosted-checkout URL it returns (including a session id Grow generates).
        String base = "/payment/mock?order=" + order.getOrderNumber();
        return order.getGuestToken() == null ? base : base + "&t=" + order.getGuestToken();
    }
}
