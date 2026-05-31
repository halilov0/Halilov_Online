package com.halilov.online.payment;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * The {@code payments} table (V20) — system of record + idempotency anchor for
 * one money movement against an order, together with the Green Invoice document
 * it produced. See {@code V20__payments.sql} for the column contract.
 *
 * <p>{@code providerOrderId} = the gateway checkout session id (PayPal Orders-v2
 * order id) returned at {@code INITIATED}. {@code providerTxnId} = the settled
 * transaction id (PayPal capture id) confirmed server-side — the idempotency +
 * refund anchor, unique per {@code (provider, providerTxnId)}.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentKind kind = PaymentKind.CHARGE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Column(name = "amount_agorot", nullable = false)
    private int amountAgorot;

    @Column(nullable = false)
    private String currency = "ILS";

    @Column(name = "provider_order_id")
    private String providerOrderId;

    @Column(name = "provider_txn_id")
    private String providerTxnId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gi_status")
    private GiStatus giStatus;

    @Column(name = "gi_doc_id")
    private String giDocId;

    @Column(name = "gi_doc_number")
    private String giDocNumber;

    @Column(name = "gi_doc_type")
    private Integer giDocType;

    @Column(name = "gi_doc_url")
    private String giDocUrl;

    @Column(name = "raw_callback")
    private String rawCallback;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public PaymentKind getKind() { return kind; }
    public void setKind(PaymentKind kind) { this.kind = kind; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public int getAmountAgorot() { return amountAgorot; }
    public void setAmountAgorot(int amountAgorot) { this.amountAgorot = amountAgorot; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getProviderOrderId() { return providerOrderId; }
    public void setProviderOrderId(String providerOrderId) { this.providerOrderId = providerOrderId; }
    public String getProviderTxnId() { return providerTxnId; }
    public void setProviderTxnId(String providerTxnId) { this.providerTxnId = providerTxnId; }
    public GiStatus getGiStatus() { return giStatus; }
    public void setGiStatus(GiStatus giStatus) { this.giStatus = giStatus; }
    public String getGiDocId() { return giDocId; }
    public void setGiDocId(String giDocId) { this.giDocId = giDocId; }
    public String getGiDocNumber() { return giDocNumber; }
    public void setGiDocNumber(String giDocNumber) { this.giDocNumber = giDocNumber; }
    public Integer getGiDocType() { return giDocType; }
    public void setGiDocType(Integer giDocType) { this.giDocType = giDocType; }
    public String getGiDocUrl() { return giDocUrl; }
    public void setGiDocUrl(String giDocUrl) { this.giDocUrl = giDocUrl; }
    public String getRawCallback() { return rawCallback; }
    public void setRawCallback(String rawCallback) { this.rawCallback = rawCallback; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
}
