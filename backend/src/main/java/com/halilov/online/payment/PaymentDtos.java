package com.halilov.online.payment;

/**
 * Records exchanged with {@link PaymentController}.
 * {@code InitiateResponse.redirectUrl} is the URL the SPA should
 * send the user to next; for the mock provider this is the in-app
 * mock checkout page.
 */
public final class PaymentDtos {

    public record InitiateResponse(
        String provider,
        String redirectUrl,
        String orderNumber
    ) {}

    public record MockCompleteRequest(String outcome) {}

    private PaymentDtos() {}
}
