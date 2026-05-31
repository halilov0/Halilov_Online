package com.halilov.online.payment;

/**
 * Records exchanged with {@link PaymentController}.
 *
 * <p>{@code InitiateResponse.redirectUrl} is where the SPA sends the user next:
 * for {@code paypal} it's the PayPal hosted-approval URL; for the {@code mock}
 * provider it's the in-app mock checkout page. {@code CaptureResponse} is what
 * the SPA gets back when the payer returns from PayPal — it carries the order's
 * post-capture status so the return page can route to confirmation.
 */
public final class PaymentDtos {

    public record InitiateResponse(
        String provider,
        String redirectUrl,
        String orderNumber
    ) {}

    public record MockCompleteRequest(String outcome) {}

    /** Body of the PayPal capture-on-return call. {@code paypalOrderId} = PayPal's {@code token} query param. */
    public record CaptureRequest(String paypalOrderId) {}

    public record CaptureResponse(
        String orderNumber,
        String status
    ) {}

    /** The official Green Invoice קבלה for an order, or both-null if not issued yet. */
    public record ReceiptInfo(
        String number,
        String url
    ) {}

    private PaymentDtos() {}
}
