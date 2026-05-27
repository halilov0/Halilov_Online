/**
 * Order lifecycle and supporting concepts (addresses, delivery pricing,
 * refunds, CSV export).
 *
 * <p>An {@link com.halilov.online.order.Order} is created in
 * {@code PENDING} and walks the
 * {@link com.halilov.online.order.OrderStatus} ladder:
 * {@code PENDING → PAID → FULFILLED → SHIPPED → DELIVERED}, with
 * {@code CANCELLED} and {@code REFUNDED} as terminal off-ramps.
 *
 * <p>Key invariants enforced by
 * {@link com.halilov.online.order.OrderService}:
 * <ul>
 *   <li>Stock decrement happens <em>once</em>, on the
 *       {@code PENDING → PAID} transition. Cancels and refunds restore
 *       stock; coupon usage counters move in lockstep.</li>
 *   <li>Customer self-cancel is allowed up through {@code FULFILLED}
 *       (parcel not yet picked up by the courier). After
 *       {@code SHIPPED} the customer must contact us — IL law still
 *       gives 14 days but a parcel in transit cannot be unmade.</li>
 *   <li>Wrong-account access returns <strong>403</strong>, not 404, so
 *       the SPA can distinguish "wrong account" from "wrong order
 *       number". The error body never echoes the actual owner's email
 *       — no buyer PII in error UI.</li>
 *   <li>Guest checkout mints a {@code guest_token} on create;
 *       owner-shared invoice links use a separate idempotent
 *       {@code share_token}. Anonymous reads must present one via the
 *       {@code X-Guest-Token} header or {@code ?t=} query param.</li>
 * </ul>
 *
 * <p>Delivery pricing is owned by
 * {@link com.halilov.online.order.DeliveryService} and exposed publicly
 * via {@link com.halilov.online.order.DeliveryController} so the SPA
 * can quote shipping without creating an order first. There is one
 * delivery method today: {@code COURIER}.
 */
package com.halilov.online.order;
