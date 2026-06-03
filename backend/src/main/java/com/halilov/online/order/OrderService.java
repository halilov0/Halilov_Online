package com.halilov.online.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.audit.AuditAction;
import com.halilov.online.audit.AuditService;
import com.halilov.online.catalog.Product;
import com.halilov.online.catalog.ProductRepository;
import com.halilov.online.common.Csv;
import com.halilov.online.coupon.CouponService;
import com.halilov.online.notification.EmailMessage;
import com.halilov.online.notification.EmailService;
import com.halilov.online.notification.OrderEmailBuilder;
import com.halilov.online.user.User;
import com.halilov.online.user.UserRepository;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Order lifecycle from create through refund. The state machine is
 * {@code PENDING → PAID → FULFILLED → SHIPPED → DELIVERED}, with
 * {@code CANCELLED} and {@code REFUNDED} as terminals.
 *
 * <p>Key invariants enforced here:
 * <ul>
 *   <li>Stock decrements only on {@code PENDING → PAID}; cancels and
 *       refunds restore stock; coupon usage counters mirror the
 *       transition.</li>
 *   <li>Customer self-cancel runs through {@code FULFILLED} (parcel
 *       not yet picked up). After {@code SHIPPED} the customer must
 *       contact support.</li>
 *   <li>Owner-mismatch on registered reads returns 403, never 404;
 *       error responses must not echo the actual owner's email.</li>
 *   <li>Guest orders carry a {@code guest_token} (one-shot, returned
 *       at create time) and registered orders can mint an idempotent
 *       {@code share_token} for "send the invoice to my accountant"
 *       flows.</li>
 * </ul>
 *
 * <p>Order numbers are minted from a 32-char alphabet (dropping
 * {@code 0/1/O/I}) so a number dictated over the phone is unambiguous.
 * Eight chars ≈ 1.1e12 combos — collision odds against the UNIQUE
 * {@code order_number} index are negligible at our scale.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    // Halilov Online is a registered עוסק פטור — exempt from collecting VAT.
    // Field kept on Order for historical orders predating the exemption; new orders stay 0.

    private final OrderRepository orders;
    private final AddressRepository addresses;
    private final ProductRepository products;
    private final UserRepository users;
    private final CouponService couponService;
    private final DeliveryService deliveryService;
    private final EmailService emailService;
    private final AuditService audit;
    private final String adminBcc;
    private final String siteBaseUrl;
    private final boolean manualReceiptNotice;

    public OrderService(OrderRepository orders, AddressRepository addresses,
                        ProductRepository products, UserRepository users,
                        CouponService couponService,
                        DeliveryService deliveryService,
                        EmailService emailService,
                        AuditService audit,
                        @Value("${app.email.adminBcc:}") String adminBcc,
                        @Value("${app.email.siteBaseUrl:}") String siteBaseUrl,
                        @Value("${app.receipt.manualNotice:false}") boolean manualReceiptNotice) {
        this.orders = orders;
        this.addresses = addresses;
        this.products = products;
        this.users = users;
        this.couponService = couponService;
        this.deliveryService = deliveryService;
        this.emailService = emailService;
        this.audit = audit;
        this.adminBcc = adminBcc;
        this.siteBaseUrl = siteBaseUrl;
        this.manualReceiptNotice = manualReceiptNotice;
    }

    @Transactional
    public OrderDtos.OrderView createOrder(String email, OrderDtos.CreateOrderRequest req) {
        User user = email == null ? null : users.findByEmail(email).orElse(null);
        if (email != null && user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no user");
        }

        DeliveryMethod method = DeliveryMethod.COURIER;
        OrderDtos.ShippingRequest ship = req.shipping();
        if (ship == null
            || ship.street() == null || ship.street().isBlank()
            || ship.city() == null || ship.city().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "courier delivery requires shipping address");
        }

        // Guests must supply a contact email — that's where the receipt goes.
        String guestEmail = null;
        if (user == null) {
            guestEmail = req.guestEmail() == null ? null : req.guestEmail().trim();
            if (guestEmail == null || guestEmail.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "guest checkout requires email");
            }
        }

        // Load all products for the items in one go
        List<Long> productIds = req.items().stream().map(OrderDtos.OrderItemRequest::productId).distinct().toList();
        Map<Long, Product> byId = new HashMap<>();
        for (Product p : products.findAllById(productIds)) byId.put(p.getId(), p);

        Address addr = null;
        if (ship != null && ship.fullName() != null && !ship.fullName().isBlank()) {
            addr = new Address();
            addr.setUserId(user == null ? null : user.getId());
            addr.setFullName(ship.fullName());
            addr.setPhone(ship.phone());
            addr.setStreet(nz(ship.street()));
            addr.setHouseNo(ship.houseNo());
            addr.setApartment(ship.apartment());
            addr.setCity(nz(ship.city()));
            addr.setPostalCode(ship.postalCode());
            addr.setNotes(ship.notes());
            addr = addresses.save(addr);
        }

        Order order = new Order();
        order.setUserId(user == null ? null : user.getId());
        order.setStatus(OrderStatus.PENDING);
        order.setDeliveryMethod(method);
        if (addr != null) order.setShippingAddressId(addr.getId());
        if (user == null) {
            order.setGuestEmail(guestEmail);
            order.setGuestToken(generateGuestToken());
        }

        int subtotal = 0;
        for (OrderDtos.OrderItemRequest itemReq : req.items()) {
            Product p = byId.get(itemReq.productId());
            if (p == null || !p.isActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "product unavailable: " + itemReq.productId());
            }
            if (itemReq.quantity() > p.getStockQty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "not enough stock for " + p.getNameHe());
            }
            int lineTotal = p.getPriceAgorot() * itemReq.quantity();
            OrderItem oi = new OrderItem();
            oi.setProductId(p.getId());
            oi.setNameHe(p.getNameHe());
            oi.setSku(p.getSku());
            oi.setUnitPriceAgorot(p.getPriceAgorot());
            oi.setQuantity(itemReq.quantity());
            oi.setLineTotalAgorot(lineTotal);
            order.addItem(oi);
            subtotal += lineTotal;
        }

        var applied = couponService.resolveForOrder(req.couponCode(), subtotal).orElse(null);
        int discount = applied != null ? applied.discountAgorot() : 0;
        int discountedSubtotal = Math.max(0, subtotal - discount);
        int shippingAgorot = deliveryService.priceFor(method, discountedSubtotal);
        int gross = discountedSubtotal + shippingAgorot;
        order.setSubtotalAgorot(subtotal);
        order.setDiscountAgorot(discount);
        order.setShippingAgorot(shippingAgorot);
        if (applied != null) order.setCouponCode(applied.code());
        order.setVatAgorot(0);
        order.setTotalAgorot(gross);
        order.setOrderNumber(generateOrderNumber());

        order = orders.save(order);
        if (user != null) {
            audit.recordAs(user.getId(), user.getEmail(), user.getRole().name(),
                AuditAction.ORDER_PLACED, "order", order.getOrderNumber(),
                "הזמנה נוצרה: " + order.getOrderNumber() + " (" + order.getTotalAgorot() + " אגורות)", null);
        } else {
            audit.recordAs(null, guestEmail, "GUEST",
                AuditAction.ORDER_PLACED, "order", order.getOrderNumber(),
                "הזמנת אורח: " + order.getOrderNumber() + " (" + order.getTotalAgorot() + " אגורות)", null);
        }
        return OrderDtos.OrderView.from(order, addr, user == null);
    }

    /**
     * Anonymous order lookup. The caller proves ownership by holding the
     * random token returned at create time — order numbers alone are too
     * predictable (millis + 4-digit rand) to gate access on.
     */
    @Transactional(readOnly = true)
    public OrderDtos.OrderView getByToken(String orderNumber, String token) {
        Order order = loadByGuestToken(orderNumber, token);
        Address addr = order.getShippingAddressId() != null
            ? addresses.findById(order.getShippingAddressId()).orElse(null)
            : null;
        return OrderDtos.OrderView.from(order, addr);
    }

    /**
     * Used by PaymentService for guest payment calls, and by the read path
     * for both guest checkout and registered-order share links — the token
     * may match either the order's guest_token (guest checkout) or its
     * share_token (owner-minted "send to accountant" link).
     */
    public Order loadByGuestToken(String orderNumber, String token) {
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        if (token == null || !tokenMatches(order, token)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
        }
        return order;
    }

    private static boolean tokenMatches(Order order, String token) {
        return (order.getGuestToken() != null && order.getGuestToken().equals(token))
            || (order.getShareToken() != null && order.getShareToken().equals(token));
    }

    /**
     * Mints (or returns) the share-token for an order the caller owns.
     * Idempotent — the same token is reused so revisiting "share" doesn't
     * keep invalidating prior recipients.
     */
    @Transactional
    public String mintShareToken(String email, String orderNumber) {
        User user = users.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no user"));
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        if (!user.getId().equals(order.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "order not on this account");
        }
        if (order.getShareToken() == null) {
            order.setShareToken(generateGuestToken());
        }
        return order.getShareToken();
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderView getMine(String email, String orderNumber) {
        User user = users.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no user"));
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        if (!user.getId().equals(order.getUserId())) {
            // The order exists but belongs to someone else. 403 lets the SPA
            // distinguish "wrong account" from "wrong order number" without
            // leaking the owner's identity. Existence-leak is acceptable
            // here — order numbers are scoped to this authenticated session.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "order not on this account");
        }
        Address addr = order.getShippingAddressId() != null
            ? addresses.findById(order.getShippingAddressId()).orElse(null)
            : null;
        return OrderDtos.OrderView.from(order, addr);
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.OrderView> listMine(String email) {
        User user = users.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no user"));
        return orders.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
            .map(o -> OrderDtos.OrderView.from(o,
                o.getShippingAddressId() != null
                    ? addresses.findById(o.getShippingAddressId()).orElse(null)
                    : null))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDtos.OrderView> adminListAll() {
        return orders.findAllByOrderByCreatedAtDesc().stream()
            .map(o -> OrderDtos.OrderView.from(o,
                o.getShippingAddressId() != null
                    ? addresses.findById(o.getShippingAddressId()).orElse(null)
                    : null))
            .toList();
    }

    @Transactional(readOnly = true)
    public String exportOrdersCsv() {
        StringBuilder out = new StringBuilder(Csv.BOM);
        out.append(Csv.row(
            "orderNumber", "createdAt", "status",
            "subtotalAgorot", "discountAgorot", "shippingAgorot", "vatAgorot", "totalAgorot",
            "couponCode", "itemCount",
            "customerName", "phone", "street", "houseNo", "city", "postalCode"
        ));
        for (Order o : orders.findAllByOrderByCreatedAtDesc()) {
            Address a = o.getShippingAddressId() != null
                ? addresses.findById(o.getShippingAddressId()).orElse(null)
                : null;
            int itemCount = o.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
            out.append(Csv.row(
                o.getOrderNumber(),
                o.getCreatedAt().toString(),
                o.getStatus().name(),
                o.getSubtotalAgorot(),
                o.getDiscountAgorot(),
                o.getShippingAgorot(),
                o.getVatAgorot(),
                o.getTotalAgorot(),
                o.getCouponCode() == null ? "" : o.getCouponCode(),
                itemCount,
                a == null ? "" : nz(a.getFullName()),
                a == null ? "" : nz(a.getPhone()),
                a == null ? "" : nz(a.getStreet()),
                a == null ? "" : nz(a.getHouseNo()),
                a == null ? "" : nz(a.getCity()),
                a == null ? "" : nz(a.getPostalCode())
            ));
        }
        return out.toString();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    @Transactional(readOnly = true)
    public OrderDtos.OrderView adminGet(String orderNumber) {
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        Address addr = order.getShippingAddressId() != null
            ? addresses.findById(order.getShippingAddressId()).orElse(null)
            : null;
        return OrderDtos.OrderView.from(order, addr);
    }

    @Transactional
    public OrderDtos.OrderView adminUpdateStatus(String orderNumber, OrderStatus newStatus) {
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));

        OrderStatus old = order.getStatus();
        if (old == newStatus) {
            return OrderDtos.OrderView.from(order,
                order.getShippingAddressId() != null
                    ? addresses.findById(order.getShippingAddressId()).orElse(null) : null);
        }

        // Decrement stock once on PENDING -> PAID
        if (old == OrderStatus.PENDING && newStatus == OrderStatus.PAID) {
            decrementStockForPaid(order);
            order.setPaidAt(java.time.Instant.now());
        }

        order.setStatus(newStatus);
        audit.record(AuditAction.ORDER_STATUS_CHANGED, "order", order.getOrderNumber(),
            "סטטוס הזמנה השתנה: " + order.getOrderNumber() + " " + old.name() + " → " + newStatus.name());
        Address addr = order.getShippingAddressId() != null
            ? addresses.findById(order.getShippingAddressId()).orElse(null) : null;

        if (old == OrderStatus.PENDING && newStatus == OrderStatus.PAID) {
            afterPaidCommit(order, addr);
        }

        return OrderDtos.OrderView.from(order, addr);
    }

    /**
     * Gateway-driven PAID flip — invoked from the payment package once a charge
     * is captured (PayPal return + signed webhook), not from an admin/human
     * request. Same side effects as the admin PENDING→PAID transition (stock
     * decrement, coupon bump, paid email) but idempotent (only fires from
     * PENDING) and audited as a {@code PAYMENT} actor since there is no
     * SecurityContext on a webhook/return. {@code providerRef} = the capture id,
     * mirrored onto {@code orders.payment_ref}.
     */
    @Transactional
    public void markPaidByPayment(String orderNumber, String providerRef) {
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            return; // already paid / cancelled — idempotent no-op
        }
        decrementStockForPaid(order);
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(java.time.Instant.now());
        if (providerRef != null && !providerRef.isBlank()) {
            order.setPaymentRef(providerRef);
        }
        Address addr = addressOf(order);
        BuyerContact buyer = resolveBuyer(order, addr);
        audit.recordAs(order.getUserId(), buyer != null ? buyer.email() : null, "PAYMENT",
            AuditAction.PAYMENT_CAPTURED, "order", order.getOrderNumber(),
            "תשלום אושר: " + order.getOrderNumber()
                + (providerRef != null ? " (txn " + providerRef + ")" : ""), null);
        afterPaidCommit(order, addr);
    }

    private void decrementStockForPaid(Order order) {
        for (OrderItem oi : order.getItems()) {
            Product p = products.findById(oi.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                    "product missing: " + oi.getProductId()));
            if (p.getStockQty() < oi.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "not enough stock to fulfill: " + p.getNameHe());
            }
            p.setStockQty(p.getStockQty() - oi.getQuantity());
        }
    }

    private void afterPaidCommit(Order order, Address addr) {
        if (order.getCouponCode() != null) {
            try {
                couponService.incrementUsage(order.getCouponCode());
            } catch (Exception e) {
                log.warn("failed to bump coupon usage for {}: {}", order.getOrderNumber(), e.toString());
            }
        }
        sendOrderPaidEmail(order, addr);
    }

    /** Everything the receipt issuer (Green Invoice) needs for one paid order. */
    public record ReceiptContext(Order order, Address address, String buyerEmail, String buyerName) {}

    /**
     * Resolve the receipt context for a paid order by its DB id (the
     * {@code payments.order_id}). Read-only; order line items are eager so the
     * returned entity stays usable for building the GI document.
     */
    @Transactional(readOnly = true)
    public ReceiptContext receiptContext(Long orderId) {
        Order order = orders.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        Address addr = addressOf(order);
        BuyerContact buyer = resolveBuyer(order, addr);
        return new ReceiptContext(order, addr,
            buyer != null ? buyer.email() : null,
            buyer != null ? buyer.name() : null);
    }

    /**
     * Customer self-cancel. Allowed before the courier picks up the parcel
     * (PENDING / PAID / FULFILLED). After SHIPPED the customer must contact us
     * — IL law still gives 14 days but a parcel in transit cannot be unmade.
     */
    @Transactional
    public OrderDtos.OrderView customerCancel(String email, String orderNumber, String reason) {
        User user = users.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no user"));
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        if (!user.getId().equals(order.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
        }
        OrderStatus s = order.getStatus();
        if (s != OrderStatus.PENDING && s != OrderStatus.PAID && s != OrderStatus.FULFILLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "ההזמנה כבר נשלחה — צרו איתנו קשר לביטול");
        }
        boolean wasPaid = (s == OrderStatus.PAID || s == OrderStatus.FULFILLED);
        applyCancel(order, "CUSTOMER", reason, wasPaid);
        Address addr = addressOf(order);
        sendCancelEmail(order, addr, wasPaid);
        audit.recordAs(user.getId(), user.getEmail(), user.getRole().name(),
            AuditAction.ORDER_CANCELLED, "order", order.getOrderNumber(),
            "לקוח ביטל הזמנה: " + order.getOrderNumber(), null);
        return OrderDtos.OrderView.from(order, addr);
    }

    /**
     * Admin issues a refund. Amount in agorot, 0 < amount <= total. If
     * {@code restoreStock} is true (default for full refunds) stock and coupon
     * usage are reversed. Order status flips to REFUNDED regardless of partial
     * vs full — the amount column carries the truth.
     */
    @Transactional
    public OrderDtos.OrderView adminRefund(String orderNumber, int amountAgorot, String reason, Boolean restoreStockFlag) {
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        if (amountAgorot <= 0 || amountAgorot > order.getTotalAgorot()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "amount must be 1..totalAgorot");
        }
        OrderStatus s = order.getStatus();
        if (s == OrderStatus.PENDING || s == OrderStatus.CANCELLED || s == OrderStatus.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "cannot refund order in status " + s);
        }
        boolean restore = restoreStockFlag != null
            ? restoreStockFlag
            : (amountAgorot == order.getTotalAgorot());

        if (restore) {
            restoreStockAndCoupon(order);
        }
        order.setStatus(OrderStatus.REFUNDED);
        order.setRefundedAt(java.time.Instant.now());
        order.setRefundAmountAgorot(amountAgorot);
        if (reason != null && !reason.isBlank()) {
            order.setCancellationReason(reason);
        }
        if (order.getCancelledBy() == null) {
            order.setCancelledBy("ADMIN");
        }
        Address addr = addressOf(order);
        sendRefundEmail(order, addr, amountAgorot);
        audit.record(AuditAction.ORDER_REFUNDED, "order", order.getOrderNumber(),
            "זיכוי הוצא: " + order.getOrderNumber() + " (" + amountAgorot + " אגורות)");
        return OrderDtos.OrderView.from(order, addr);
    }

    private void applyCancel(Order order, String by, String reason, boolean wasPaid) {
        if (wasPaid) {
            restoreStockAndCoupon(order);
            order.setRefundedAt(java.time.Instant.now());
            order.setRefundAmountAgorot(order.getTotalAgorot());
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(java.time.Instant.now());
        order.setCancelledBy(by);
        order.setCancellationReason(reason);
    }

    private void restoreStockAndCoupon(Order order) {
        for (OrderItem oi : order.getItems()) {
            products.findById(oi.getProductId()).ifPresent(p ->
                p.setStockQty(p.getStockQty() + oi.getQuantity()));
        }
        if (order.getCouponCode() != null) {
            try {
                couponService.decrementUsage(order.getCouponCode());
            } catch (Exception e) {
                log.warn("failed to reverse coupon usage for {}: {}", order.getOrderNumber(), e.toString());
            }
        }
    }

    private Address addressOf(Order order) {
        return order.getShippingAddressId() != null
            ? addresses.findById(order.getShippingAddressId()).orElse(null)
            : null;
    }

    /**
     * Resolve who to email + display name. Falls back to the guest contact
     * captured at checkout when the order has no linked user.
     */
    private BuyerContact resolveBuyer(Order order, Address addr) {
        String email = null;
        String fallbackName = null;
        if (order.getUserId() != null) {
            User buyer = users.findById(order.getUserId()).orElse(null);
            if (buyer != null) {
                email = buyer.getEmail();
                fallbackName = buyer.getFullName();
            }
        } else if (order.getGuestEmail() != null) {
            email = order.getGuestEmail();
        }
        if (email == null) return null;
        String name = addr != null && addr.getFullName() != null && !addr.getFullName().isBlank()
            ? addr.getFullName() : (fallbackName != null ? fallbackName : "לקוח/ה");
        return new BuyerContact(email, name);
    }

    private record BuyerContact(String email, String name) {}

    private void sendCancelEmail(Order order, Address addr, boolean wasPaid) {
        try {
            BuyerContact buyer = resolveBuyer(order, addr);
            if (buyer == null) return;
            List<String> bcc = new ArrayList<>();
            if (adminBcc != null && !adminBcc.isBlank()) bcc.add(adminBcc.trim());
            emailService.send(new EmailMessage(
                buyer.email(),
                buyer.name(),
                OrderEmailBuilder.cancelSubject(order),
                OrderEmailBuilder.cancelHtml(order, buyer.name(), wasPaid, siteBaseUrl),
                bcc
            ));
        } catch (Exception e) {
            log.warn("failed to send cancel email for {}: {}", order.getOrderNumber(), e.toString());
        }
    }

    private void sendRefundEmail(Order order, Address addr, int amountAgorot) {
        try {
            BuyerContact buyer = resolveBuyer(order, addr);
            if (buyer == null) return;
            List<String> bcc = new ArrayList<>();
            if (adminBcc != null && !adminBcc.isBlank()) bcc.add(adminBcc.trim());
            emailService.send(new EmailMessage(
                buyer.email(),
                buyer.name(),
                OrderEmailBuilder.refundSubject(order),
                OrderEmailBuilder.refundHtml(order, buyer.name(), amountAgorot, siteBaseUrl),
                bcc
            ));
        } catch (Exception e) {
            log.warn("failed to send refund email for {}: {}", order.getOrderNumber(), e.toString());
        }
    }

    private void sendOrderPaidEmail(Order order, Address addr) {
        try {
            BuyerContact buyer = resolveBuyer(order, addr);
            if (buyer == null) {
                log.warn("order {} has no buyer contact, skipping email", order.getOrderNumber());
                return;
            }
            List<String> bcc = new ArrayList<>();
            if (adminBcc != null && !adminBcc.isBlank()) bcc.add(adminBcc.trim());
            emailService.send(new EmailMessage(
                buyer.email(),
                buyer.name(),
                OrderEmailBuilder.subject(order),
                OrderEmailBuilder.html(order, addr, buyer.name(), siteBaseUrl, manualReceiptNotice),
                bcc
            ));
        } catch (Exception e) {
            log.warn("failed to send order paid email for {}: {}", order.getOrderNumber(), e.toString());
        }
    }

    // 32-char alphabet minus 0/1/O/I to keep numbers easy to dictate over phone.
    // 8 chars = 32^8 ≈ 1.1e12 combos — collision odds against the UNIQUE
    // order_number index are negligible at this scale; DB throws if it ever hits.
    private static final char[] ORDER_NUMBER_ALPHABET =
        "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    private String generateOrderNumber() {
        char[] out = new char[8];
        for (int i = 0; i < out.length; i++) {
            out[i] = ORDER_NUMBER_ALPHABET[RNG.nextInt(ORDER_NUMBER_ALPHABET.length)];
        }
        return "HO-" + new String(out);
    }

    private String generateGuestToken() {
        byte[] buf = new byte[24];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
