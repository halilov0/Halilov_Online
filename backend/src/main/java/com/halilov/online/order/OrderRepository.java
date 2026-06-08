package com.halilov.online.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Order> findAllByOrderByCreatedAtDesc();

    /** Once-per-customer enforcement: has this account already used the code on
     *  a non-cancelled order? (A cancelled order reversed its usage, so it
     *  doesn't count against the customer.) */
    boolean existsByUserIdAndCouponCodeIgnoreCaseAndStatusNot(
        Long userId, String couponCode, OrderStatus status);

    /** Guest variant of the above, matched by the contact email. */
    boolean existsByGuestEmailIgnoreCaseAndCouponCodeIgnoreCaseAndStatusNot(
        String guestEmail, String couponCode, OrderStatus status);
}
