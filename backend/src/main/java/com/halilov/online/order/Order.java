package com.halilov.online.order;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code orders} table. Each row owns its line items
 * ({@link OrderItem}) via cascade.
 *
 * <p>Money lives entirely in integer agorot. Address is stored by
 * {@code shipping_address_id} pointing at a snapshot row in
 * {@code addresses} — orders never reach back into
 * {@code saved_addresses} so a future profile edit can't rewrite a
 * shipped invoice. {@code guest_token} is set at create time for
 * guest checkout; {@code share_token} is minted on demand by the
 * owner for accountant-style share links. {@code force_logout_at},
 * cancel/refund timestamps and reason fields support the post-pay
 * lifecycle.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false)
    private DeliveryMethod deliveryMethod = DeliveryMethod.COURIER;

    @Column(name = "subtotal_agorot", nullable = false)
    private int subtotalAgorot;

    @Column(name = "shipping_agorot", nullable = false)
    private int shippingAgorot;

    @Column(name = "vat_agorot", nullable = false)
    private int vatAgorot;

    @Column(name = "discount_agorot", nullable = false)
    private int discountAgorot;

    @Column(name = "coupon_code")
    private String couponCode;

    @Column(name = "total_agorot", nullable = false)
    private int totalAgorot;

    @Column(name = "shipping_address_id")
    private Long shippingAddressId;

    @Column(name = "guest_email")
    private String guestEmail;

    @Column(name = "guest_token")
    private String guestToken;

    @Column(name = "share_token")
    private String shareToken;

    @Column(name = "payment_ref")
    private String paymentRef;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refund_amount_agorot")
    private Integer refundAmountAgorot;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public DeliveryMethod getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(DeliveryMethod deliveryMethod) { this.deliveryMethod = deliveryMethod; }
    public int getSubtotalAgorot() { return subtotalAgorot; }
    public void setSubtotalAgorot(int subtotalAgorot) { this.subtotalAgorot = subtotalAgorot; }
    public int getShippingAgorot() { return shippingAgorot; }
    public void setShippingAgorot(int shippingAgorot) { this.shippingAgorot = shippingAgorot; }
    public int getVatAgorot() { return vatAgorot; }
    public void setVatAgorot(int vatAgorot) { this.vatAgorot = vatAgorot; }
    public int getDiscountAgorot() { return discountAgorot; }
    public void setDiscountAgorot(int discountAgorot) { this.discountAgorot = discountAgorot; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public int getTotalAgorot() { return totalAgorot; }
    public void setTotalAgorot(int totalAgorot) { this.totalAgorot = totalAgorot; }
    public Long getShippingAddressId() { return shippingAddressId; }
    public void setShippingAddressId(Long shippingAddressId) { this.shippingAddressId = shippingAddressId; }
    public String getGuestEmail() { return guestEmail; }
    public void setGuestEmail(String guestEmail) { this.guestEmail = guestEmail; }
    public String getGuestToken() { return guestToken; }
    public void setGuestToken(String guestToken) { this.guestToken = guestToken; }
    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }
    public String getPaymentRef() { return paymentRef; }
    public void setPaymentRef(String paymentRef) { this.paymentRef = paymentRef; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    public Instant getRefundedAt() { return refundedAt; }
    public void setRefundedAt(Instant refundedAt) { this.refundedAt = refundedAt; }
    public Integer getRefundAmountAgorot() { return refundAmountAgorot; }
    public void setRefundAmountAgorot(Integer refundAmountAgorot) { this.refundAmountAgorot = refundAmountAgorot; }
    public List<OrderItem> getItems() { return items; }
}
