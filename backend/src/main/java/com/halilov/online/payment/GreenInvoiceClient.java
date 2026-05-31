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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Green Invoice ("morning") — issues the legal קבלה (receipt, document type 400)
 * for every PAID charge. The account is configured as עוסק פטור, so income lines
 * carry {@code vatType=EXEMPT (2)} and the document shows no VAT (verified
 * {@code vatRate:0} in sandbox).
 *
 * <p>Auth is a JWT minted from the API key id+secret ({@code POST /account/token},
 * ~1h TTL, cached). The receipt's payment line is recorded as PayPal
 * ({@code type=5}). Money is sent in shekels (the API uses major units), not agorot.
 */
@Component
public class GreenInvoiceClient {

    private static final Logger log = LoggerFactory.getLogger(GreenInvoiceClient.class);

    private static final int VAT_TYPE_EXEMPT = 2;   // IncomeVatType.EXEMPT
    private static final int PAYMENT_TYPE_PAYPAL = 5; // PaymentType.PAYPAL
    private static final int DEAL_TYPE_REGULAR = 1;   // PaymentDealType.REGULAR
    private static final ZoneId IL = ZoneId.of("Asia/Jerusalem");

    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKeyId;
    private final String apiKeySecret;
    private final int documentType;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;
    private final Object tokenLock = new Object();

    public GreenInvoiceClient(
        @Value("${app.greenInvoice.baseUrl:https://sandbox.d.greeninvoice.co.il/api/v1}") String baseUrl,
        @Value("${app.greenInvoice.apiKeyId:}") String apiKeyId,
        @Value("${app.greenInvoice.apiKeySecret:}") String apiKeySecret,
        @Value("${app.greenInvoice.documentType:400}") int documentType
    ) {
        this.apiKeyId = apiKeyId;
        this.apiKeySecret = apiKeySecret;
        this.documentType = documentType;
        this.http = RestClient.builder().baseUrl(baseUrl.replaceAll("/+$", "")).build();
    }

    public boolean isConfigured() {
        return apiKeyId != null && !apiKeyId.isBlank()
            && apiKeySecret != null && !apiKeySecret.isBlank();
    }

    /** The GI document type issued for a charge (400 = קבלה). */
    public int documentType() {
        return documentType;
    }

    /** One income (sale) line on the receipt. */
    public record Line(String description, int quantity, int unitPriceAgorot) {}

    /** What to put on a receipt for one paid order. */
    public record ReceiptRequest(
        String buyerName, String buyerEmail,
        List<Line> lines, int shippingAgorot, int discountAgorot, String couponCode,
        int totalAgorot
    ) {}

    /** The issued GI document. */
    public record Receipt(String docId, String number, String url) {}

    public Receipt issueReceipt(ReceiptRequest req) {
        List<Map<String, Object>> income = new ArrayList<>();
        for (Line l : req.lines()) {
            income.add(incomeLine(l.description(), l.quantity(), l.unitPriceAgorot()));
        }
        if (req.shippingAgorot() > 0) {
            income.add(incomeLine("משלוח", 1, req.shippingAgorot()));
        }
        if (req.discountAgorot() > 0) {
            String label = "הנחה" + (req.couponCode() != null && !req.couponCode().isBlank()
                ? " (" + req.couponCode() + ")" : "");
            income.add(incomeLine(label, 1, -req.discountAgorot()));
        }

        Map<String, Object> client = new LinkedHashMap<>();
        client.put("name", req.buyerName() == null || req.buyerName().isBlank() ? "לקוח/ה" : req.buyerName());
        if (req.buyerEmail() != null && !req.buyerEmail().isBlank()) {
            client.put("emails", List.of(req.buyerEmail()));
        }
        client.put("add", false); // don't persist this client into the GI address book

        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("type", PAYMENT_TYPE_PAYPAL);
        payment.put("price", shekels(req.totalAgorot()));
        payment.put("currency", "ILS");
        payment.put("date", LocalDate.now(IL).toString());
        payment.put("dealType", DEAL_TYPE_REGULAR);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", documentType);
        body.put("lang", "he");
        body.put("currency", "ILS");
        body.put("client", client);
        body.put("income", income);
        body.put("payment", List.of(payment));

        JsonNode res = post("/documents", body);
        String id = res.path("id").asText(null);
        String number = res.path("number").asText(null);
        JsonNode url = res.path("url");
        String link = firstNonBlank(
            url.path("he").asText(null), url.path("origin").asText(null), url.path("en").asText(null));
        if (id == null) {
            throw new IllegalStateException("GI document response missing id: " + res);
        }
        return new Receipt(id, number, link);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private Map<String, Object> incomeLine(String description, int quantity, int amountAgorot) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("description", description);
        line.put("quantity", quantity);
        line.put("price", shekels(amountAgorot));
        line.put("currency", "ILS");
        line.put("vatType", VAT_TYPE_EXEMPT);
        return line;
    }

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
            log.warn("GreenInvoice POST {} -> {} {}", path, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("GreenInvoice POST " + path + " failed: " + e, e);
        }
    }

    private String token() {
        String snapshot = cachedToken;
        if (snapshot != null && Instant.now().isBefore(tokenExpiry)) return snapshot;
        synchronized (tokenLock) {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) return cachedToken;
            if (!isConfigured()) {
                throw new IllegalStateException("GreenInvoice not configured (apiKeyId/secret empty)");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", apiKeyId);
            body.put("secret", apiKeySecret);
            try {
                String resp = http.post().uri("/account/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsString(body))
                    .retrieve().body(String.class);
                JsonNode node = mapper.readTree(resp);
                cachedToken = node.path("token").asText(null);
                long expiresEpoch = node.path("expires").asLong(0);
                Instant exp = expiresEpoch > 0
                    ? Instant.ofEpochSecond(expiresEpoch).minusSeconds(60)
                    : Instant.now().plusSeconds(1800);
                tokenExpiry = exp;
                if (cachedToken == null) throw new IllegalStateException("no token in response");
                return cachedToken;
            } catch (Exception e) {
                throw new IllegalStateException("GreenInvoice token request failed: " + e, e);
            }
        }
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }

    /** Agorot → shekels as a JSON number with 2 decimals (14990 → 149.90, -3198 → -31.98). */
    private static BigDecimal shekels(int amountAgorot) {
        return BigDecimal.valueOf(amountAgorot, 2);
    }
}
