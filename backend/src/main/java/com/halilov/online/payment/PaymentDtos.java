package com.halilov.online.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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

    /**
     * Admin records a manually-issued Green Invoice קבלה for a paid order
     * (lean launch — receipts created by hand until the GI API is enabled).
     * {@code url} is optional: when present the invoice page links the קבלה.
     */
    public record ManualReceiptRequest(
        @NotBlank @Size(max = 64) String number,
        @Size(max = 1024) String url
    ) {}

    private PaymentDtos() {}
}
