package com.halilov.online.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.catalog.Product;
import com.halilov.online.catalog.ProductRepository;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Back-in-stock sign-up + notify.
 *
 * <p>{@link #subscribe} validates and stores a (product, email) row.
 * When stock for a product goes from 0 back to positive,
 * {@link com.halilov.online.catalog.CatalogService} calls
 * {@code triggerForProduct} which dequeues pending sign-ups and hands
 * one {@link EmailMessage} each to {@link EmailService} — the outbox
 * absorbs Brevo rate-limits and retries on its own cadence.
 *
 * <p>Anti-abuse lives in {@link StockNotificationController}
 * (per-IP + per-email throttle); this service trusts its inputs.
 */
@Service
public class StockNotificationService {

    private static final Logger log = LoggerFactory.getLogger(StockNotificationService.class);
    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_EMAIL_LEN = 255;

    private final StockNotificationRepository repo;
    private final ProductRepository products;
    private final EmailService emailService;
    private final String siteBaseUrl;

    public StockNotificationService(
        StockNotificationRepository repo,
        ProductRepository products,
        EmailService emailService,
        @Value("${app.email.siteBaseUrl:}") String siteBaseUrl
    ) {
        this.repo = repo;
        this.products = products;
        this.emailService = emailService;
        this.siteBaseUrl = siteBaseUrl;
    }

    @Transactional
    public void subscribe(Long productId, String emailRaw) {
        if (emailRaw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email required");
        }
        String email = emailRaw.trim().toLowerCase();
        if (email.isEmpty() || email.length() > MAX_EMAIL_LEN || !EMAIL_RE.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid email");
        }
        Product p = products.findById(productId)
            .filter(Product::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
        if (p.getStockQty() > 0) {
            return;
        }
        if (repo.findFirstByProductIdAndEmailAndNotifiedAtIsNull(productId, email).isPresent()) {
            return;
        }
        StockNotification row = new StockNotification();
        row.setProductId(productId);
        row.setEmail(email);
        repo.save(row);
    }

    @Transactional
    public void notifyRestocked(Long productId) {
        List<StockNotification> pending = repo.findByProductIdAndNotifiedAtIsNull(productId);
        if (pending.isEmpty()) return;
        Product p = products.findById(productId).orElse(null);
        if (p == null) return;

        String subject = BackInStockEmailBuilder.subject(p);
        String html = BackInStockEmailBuilder.html(p, siteBaseUrl);
        Instant now = Instant.now();
        int queued = 0;
        for (StockNotification s : pending) {
            try {
                emailService.send(new EmailMessage(s.getEmail(), null, subject, html));
                queued++;
            } catch (Exception e) {
                log.warn("failed to enqueue back-in-stock email id={} product={}: {}",
                    s.getId(), productId, e.toString());
            }
            s.setNotifiedAt(now);
        }
        log.info("[stock-notify] queued {}/{} back-in-stock emails for product={}",
            queued, pending.size(), productId);
    }
}
