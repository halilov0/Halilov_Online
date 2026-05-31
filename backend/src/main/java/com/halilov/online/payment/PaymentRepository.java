package com.halilov.online.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** The PayPal order id is unique per checkout attempt — used by the return + webhook paths. */
    Optional<Payment> findFirstByProviderOrderIdOrderByIdDesc(String providerOrderId);

    /** Latest charge row for an order (the one to capture when no PayPal order id is supplied). */
    Optional<Payment> findFirstByOrderIdAndKindOrderByIdDesc(Long orderId, PaymentKind kind);

    /** Idempotency guard: a verified capture maps to exactly one charge row. */
    boolean existsByProviderAndProviderTxnId(String provider, String providerTxnId);

    /** Retry sweep target — paid charges whose receipt is still pending/failed (oldest first). */
    List<Payment> findTop50ByGiStatusInOrderByCreatedAtAsc(Collection<GiStatus> giStatuses);
}
