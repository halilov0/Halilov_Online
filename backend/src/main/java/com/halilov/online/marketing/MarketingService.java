package com.halilov.online.marketing;

import com.halilov.online.notification.EmailMessage;
import com.halilov.online.notification.EmailService;
import com.halilov.online.user.User;
import com.halilov.online.user.UserRepository;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Broadcast a marketing email to every opted-in user.
 * Each message is queued via {@link EmailService} so the outbox handles
 * retries / Brevo rate limits. A one-click unsubscribe footer is appended
 * per-recipient using their stable token.
 */
@Service
public class MarketingService {

    private static final Logger log = LoggerFactory.getLogger(MarketingService.class);

    private final UserRepository users;
    private final EmailService emails;
    private final String siteBaseUrl;

    public MarketingService(
        UserRepository users,
        EmailService emails,
        @Value("${app.email.siteBaseUrl:}") String siteBaseUrl
    ) {
        this.users = users;
        this.emails = emails;
        this.siteBaseUrl = siteBaseUrl == null ? "" : siteBaseUrl.replaceAll("/+$", "");
    }

    public long eligibleCount() {
        return users.countByMarketingOptInTrueAndEnabledTrue();
    }

    @Transactional
    public MarketingDtos.BroadcastResult broadcast(MarketingDtos.BroadcastRequest req) {
        // Even though only admin can author this HTML, sanitize before send:
        // an admin pasting a template from somewhere could smuggle event
        // handlers / external <script>/<iframe> that hurts deliverability
        // (Brevo's spam classifier penalizes risky markup) or fetches
        // tracking pixels we never approved.
        String safeBody = sanitize(req.htmlBody());
        List<User> recipients = users.findAllByMarketingOptInTrueAndEnabledTrue();
        int queued = 0;
        for (User u : recipients) {
            if (u.getUnsubscribeToken() == null || u.getUnsubscribeToken().isBlank()) {
                u.setUnsubscribeToken(UUID.randomUUID().toString().replace("-", ""));
            }
            String html = wrap(safeBody, u.getUnsubscribeToken());
            try {
                emails.send(new EmailMessage(u.getEmail(), u.getFullName(), req.subject(), html));
                queued++;
            } catch (Exception e) {
                log.warn("[marketing] queue failed user={} err={}", u.getEmail(), e.toString());
            }
        }
        log.info("[marketing] broadcast subject='{}' queued={} total={}",
            req.subject(), queued, recipients.size());
        return new MarketingDtos.BroadcastResult(queued, recipients.size());
    }

    @Transactional
    public String unsubscribe(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing token");
        }
        User u = users.findByUnsubscribeToken(token)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "invalid token"));
        if (u.isMarketingOptIn()) {
            u.setMarketingOptIn(false);
            u.setMarketingConsentAt(Instant.now());
            log.info("[marketing] unsubscribed user={}", u.getEmail());
        }
        return u.getEmail();
    }

    /** Allowlist of tags + attributes safe to include in a marketing email.
     *  Built once, reused per broadcast. relaxed() covers headings/lists/
     *  basic formatting; we additionally permit inline {@code style} on
     *  common containers so admin-typed templates keep their layout. */
    private static final Safelist EMAIL_SAFELIST = Safelist.relaxed()
        .addAttributes(":all", "style")
        .addAttributes("a", "target", "rel")
        .addProtocols("a", "href", "http", "https", "mailto")
        .addProtocols("img", "src", "http", "https", "data");

    private String sanitize(String html) {
        if (html == null || html.isEmpty()) return "";
        return Jsoup.clean(html, EMAIL_SAFELIST);
    }

    private String wrap(String html, String token) {
        String unsubUrl = (siteBaseUrl.isBlank() ? "" : siteBaseUrl)
            + "/api/marketing/unsubscribe?token=" + token;
        return html
            + "<hr style=\"margin:32px 0 12px;border:none;border-top:1px solid #e5e5e5\"/>"
            + "<p style=\"font-size:11px;color:#888;text-align:center;direction:rtl\">"
            + "קיבלת את המייל הזה כי הסכמת לקבל עדכונים שיווקיים מחלילוב אונליין. "
            + "<a href=\"" + unsubUrl + "\" style=\"color:#888\">להסרה מרשימת התפוצה</a>."
            + "</p>";
    }
}
