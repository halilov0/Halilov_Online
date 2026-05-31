package com.halilov.online.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PayPal Checkout (server-side Orders v2). Three calls drive the flow:
 * <ul>
 *   <li>{@link #createOrder} — {@code POST /v2/checkout/orders} (intent CAPTURE,
 *       ILS amount, our order number as {@code custom_id}); returns the payer
 *       {@code approve} link the SPA redirects to.</li>
 *   <li>{@link #captureOrder} — {@code POST /v2/checkout/orders/{id}/capture}
 *       on return; idempotent (a re-capture 422 falls back to a GET read).</li>
 *   <li>{@link #verifyWebhook} — {@code POST /v1/notifications/verify-webhook-signature}
 *       so a forged callback can't flip an order to PAID.</li>
 * </ul>
 *
 * <p>OAuth client-credentials token is cached until shortly before expiry. The
 * bean is always constructed; calls throw only when creds are missing, so the
 * mock/disabled providers wire fine without PayPal config.
 */
@Component
public class PayPalClient {

    private static final Logger log = LoggerFactory.getLogger(PayPalClient.class);

    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String clientId;
    private final String clientSecret;
    private final String currency;
    private final String webhookId;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;
    private final Object tokenLock = new Object();

    public PayPalClient(
        @Value("${app.payment.paypal.baseUrl:https://api-m.sandbox.paypal.com}") String baseUrl,
        @Value("${app.payment.paypal.clientId:}") String clientId,
        @Value("${app.payment.paypal.clientSecret:}") String clientSecret,
        @Value("${app.payment.paypal.currency:ILS}") String currency,
        @Value("${app.payment.paypal.webhookId:}") String webhookId
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.currency = currency;
        this.webhookId = webhookId;
        this.http = RestClient.builder().baseUrl(baseUrl.replaceAll("/+$", "")).build();
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
            && clientSecret != null && !clientSecret.isBlank();
    }

    /** A PayPal order ready for payer approval. */
    public record CreatedOrder(String orderId, String approveUrl) {}

    /** Result of capturing an approved order. {@code captureId} is the settled txn id. */
    public record CaptureResult(String status, String captureId) {}

    public CreatedOrder createOrder(String orderNumber, int amountAgorot, String returnUrl, String cancelUrl) {
        Map<String, Object> amount = new LinkedHashMap<>();
        amount.put("currency_code", currency);
        amount.put("value", money(amountAgorot));

        Map<String, Object> unit = new LinkedHashMap<>();
        unit.put("custom_id", orderNumber);
        unit.put("amount", amount);

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("return_url", returnUrl);
        ctx.put("cancel_url", cancelUrl);
        ctx.put("user_action", "PAY_NOW");
        ctx.put("shipping_preference", "NO_SHIPPING");
        ctx.put("brand_name", "חלילוב אונליין");
        // Land on the card form (no PayPal account needed), not the login wall —
        // IL shoppers are card-first. A "log in to PayPal" link is still offered.
        // NOTE: in the legacy application_context the card-first value is "BILLING"
        // ("GUEST_CHECKOUT" only exists in the newer payment_source experience_context).
        // Requires "PayPal Account Optional = On" in the merchant account settings.
        ctx.put("landing_page", "BILLING");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intent", "CAPTURE");
        body.put("purchase_units", List.of(unit));
        body.put("application_context", ctx);

        JsonNode res = post("/v2/checkout/orders", body);
        String id = res.path("id").asText(null);
        String approve = null;
        for (JsonNode link : res.path("links")) {
            String rel = link.path("rel").asText("");
            if ("approve".equals(rel) || "payer-action".equals(rel)) {
                approve = link.path("href").asText(null);
                break;
            }
        }
        if (id == null || approve == null) {
            throw new IllegalStateException("PayPal createOrder missing id/approve link: " + res);
        }
        return new CreatedOrder(id, approve);
    }

    public CaptureResult captureOrder(String paypalOrderId) {
        try {
            JsonNode res = post("/v2/checkout/orders/" + paypalOrderId + "/capture", Map.of());
            return readCapture(res);
        } catch (RestClientResponseException e) {
            // 422 ORDER_ALREADY_CAPTURED (or a duplicate return + webhook race) — read the
            // existing capture instead of failing, so the flip stays idempotent.
            if (e.getStatusCode().value() == 422) {
                log.info("PayPal capture 422 for order {} — reading existing capture", paypalOrderId);
                JsonNode res = get("/v2/checkout/orders/" + paypalOrderId);
                return readCapture(res);
            }
            throw e;
        }
    }

    private CaptureResult readCapture(JsonNode res) {
        String status = res.path("status").asText("");
        // capture id lives under purchase_units[0].payments.captures[0].id
        JsonNode capture = res.path("purchase_units").path(0).path("payments").path("captures").path(0);
        String captureId = capture.path("id").asText(null);
        String capStatus = capture.path("status").asText(null);
        // The order-level status is COMPLETED once captured; trust the capture row's status when present.
        return new CaptureResult(capStatus != null ? capStatus : status, captureId);
    }

    /**
     * Verifies a webhook callback's signature against PayPal. Returns false when
     * no {@code webhookId} is configured (e.g. local dev) — an unverifiable
     * callback must never be trusted.
     */
    public boolean verifyWebhook(Map<String, String> headers, String rawBody) {
        if (webhookId == null || webhookId.isBlank()) return false;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("auth_algo", header(headers, "paypal-auth-algo"));
            body.put("cert_url", header(headers, "paypal-cert-url"));
            body.put("transmission_id", header(headers, "paypal-transmission-id"));
            body.put("transmission_sig", header(headers, "paypal-transmission-sig"));
            body.put("transmission_time", header(headers, "paypal-transmission-time"));
            body.put("webhook_id", webhookId);
            body.put("webhook_event", mapper.readTree(rawBody));
            JsonNode res = post("/v1/notifications/verify-webhook-signature", body);
            return "SUCCESS".equalsIgnoreCase(res.path("verification_status").asText(""));
        } catch (Exception e) {
            log.warn("PayPal webhook verification failed: {}", e.toString());
            return false;
        }
    }

    // ── internals ───────────────────────────────────────────────────────────

    private JsonNode post(String path, Object body) {
        try {
            String json = mapper.writeValueAsString(body);
            String resp = http.post().uri(path)
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve().body(String.class);
            return mapper.readTree(resp == null ? "{}" : resp);
        } catch (RestClientResponseException e) {
            log.warn("PayPal POST {} -> {} {}", path, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("PayPal POST " + path + " failed: " + e, e);
        }
    }

    private JsonNode get(String path) {
        try {
            String resp = http.get().uri(path)
                .header("Authorization", "Bearer " + token())
                .retrieve().body(String.class);
            return mapper.readTree(resp == null ? "{}" : resp);
        } catch (Exception e) {
            throw new IllegalStateException("PayPal GET " + path + " failed: " + e, e);
        }
    }

    private String token() {
        String snapshot = cachedToken;
        if (snapshot != null && Instant.now().isBefore(tokenExpiry)) return snapshot;
        synchronized (tokenLock) {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) return cachedToken;
            if (!isConfigured()) {
                throw new IllegalStateException("PayPal not configured (clientId/secret empty)");
            }
            String basic = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
            try {
                String resp = http.post().uri("/v1/oauth2/token")
                    .header("Authorization", "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials")
                    .retrieve().body(String.class);
                JsonNode node = mapper.readTree(resp);
                cachedToken = node.path("access_token").asText(null);
                long expiresIn = node.path("expires_in").asLong(3000);
                // Refresh a minute early to avoid using a token that expires mid-call.
                tokenExpiry = Instant.now().plusSeconds(Math.max(60, expiresIn - 60));
                if (cachedToken == null) throw new IllegalStateException("no access_token in response");
                return cachedToken;
            } catch (Exception e) {
                throw new IllegalStateException("PayPal OAuth token request failed: " + e, e);
            }
        }
    }

    private static String header(Map<String, String> headers, String name) {
        // Servlet header names are case-insensitive; our map is lowercased on the way in.
        String v = headers.get(name);
        return v == null ? "" : v;
    }

    /** Agorot → PayPal decimal string ("14990" → "149.90"). */
    private static String money(int amountAgorot) {
        return BigDecimal.valueOf(amountAgorot, 2).toPlainString();
    }
}
