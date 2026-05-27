/**
 * Payment integration. Currently a single mock provider for end-to-end
 * testing of the order flow with zero PCI scope.
 *
 * <p>{@link com.halilov.online.payment.PaymentService} owns the small
 * state machine:
 * <ol>
 *   <li>{@code POST /api/orders/{n}/pay} returns a mock checkout URL.</li>
 *   <li>The SPA "redirects" to the mock page, which simulates the
 *       provider redirecting back.</li>
 *   <li>{@code POST /api/payments/mock/{n}/complete} (token-gated for
 *       guests) flips the order to {@code PAID} via
 *       {@link com.halilov.online.order.OrderService#adminUpdateStatus}.</li>
 * </ol>
 *
 * <p>Real PSP integration (Tranzila, CardCom, etc.) will replace the
 * mock provider behind the same interface — the contract with
 * {@code OrderService} is what counts. Raw card data must never reach
 * this service.
 */
package com.halilov.online.payment;
