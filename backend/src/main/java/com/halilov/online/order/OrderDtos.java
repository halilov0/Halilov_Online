package com.halilov.online.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

public class OrderDtos {

    public record OrderItemRequest(
        @NotNull Long productId,
        @Min(1) @Max(99) int quantity
    ) {}

    public record ShippingRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Size(max = 32) String phone,
        @Size(max = 255) String street,
        @Size(max = 32) String houseNo,
        @Size(max = 32) String apartment,
        @Size(max = 128) String city,
        @Size(max = 16) String postalCode,
        @Size(max = 500) String notes
    ) {}

    public record CreateOrderRequest(
        @NotEmpty @Size(max = 50) @Valid List<OrderItemRequest> items,
        @Valid ShippingRequest shipping,
        DeliveryMethod deliveryMethod,
        @Size(max = 64) String couponCode,
        @Email @Size(max = 255) String guestEmail
    ) {}

    public record UpdateStatusRequest(@NotNull OrderStatus status) {}

    public record CancelRequest(@Size(max = 500) String reason) {}

    public record RefundRequest(
        @NotNull @Min(0) Integer amountAgorot,
        @Size(max = 500) String reason,
        Boolean restoreStock
    ) {}

    public record OrderItemView(
        Long productId, String nameHe, String sku,
        int unitPriceAgorot, int quantity, int lineTotalAgorot
    ) {
        static OrderItemView from(OrderItem oi) {
            return new OrderItemView(
                oi.getProductId(), oi.getNameHe(), oi.getSku(),
                oi.getUnitPriceAgorot(), oi.getQuantity(), oi.getLineTotalAgorot()
            );
        }
    }

    public record ShippingView(
        String fullName, String phone, String street, String houseNo,
        String apartment, String city, String postalCode, String notes
    ) {
        static ShippingView from(Address a) {
            if (a == null) return null;
            return new ShippingView(
                a.getFullName(), a.getPhone(), a.getStreet(), a.getHouseNo(),
                a.getApartment(), a.getCity(), a.getPostalCode(), a.getNotes()
            );
        }
    }

    public record OrderView(
        String orderNumber, String status,
        int subtotalAgorot, int shippingAgorot, int vatAgorot,
        int discountAgorot, String couponCode,
        int totalAgorot,
        List<OrderItemView> items, ShippingView shipping, Instant createdAt,
        Instant cancelledAt, String cancellationReason, String cancelledBy,
        Instant refundedAt, Integer refundAmountAgorot,
        DeliveryMethod deliveryMethod,
        String guestEmail,
        // Only set on the create-order response for guest checkouts — lets the
        // SPA call the order/payment endpoints anonymously. Subsequent reads
        // never include it.
        String guestToken
    ) {
        static OrderView from(Order o, Address a) {
            return from(o, a, false);
        }

        static OrderView from(Order o, Address a, boolean includeGuestToken) {
            return new OrderView(
                o.getOrderNumber(), o.getStatus().name(),
                o.getSubtotalAgorot(), o.getShippingAgorot(), o.getVatAgorot(),
                o.getDiscountAgorot(), o.getCouponCode(),
                o.getTotalAgorot(),
                o.getItems().stream().map(OrderItemView::from).toList(),
                ShippingView.from(a), o.getCreatedAt(),
                o.getCancelledAt(), o.getCancellationReason(), o.getCancelledBy(),
                o.getRefundedAt(), o.getRefundAmountAgorot(),
                o.getDeliveryMethod(),
                o.getGuestEmail(),
                includeGuestToken ? o.getGuestToken() : null
            );
        }
    }
}
