/**
 * Email marketing: opt-in subscriber list and campaign send-out.
 *
 * <p>Subscription state lives on the {@link com.halilov.online.user.User}
 * entity ({@code marketing_opt_in}, {@code marketing_consent_at},
 * {@code unsubscribe_token}). The opt-in is recorded at registration
 * and can be flipped from the account settings page.
 *
 * <p>{@link com.halilov.online.marketing.MarketingService#sendCampaign}
 * loops over opted-in users, sanitises the admin-typed HTML body with
 * jsoup's {@code Cleaner} (strips scripts, event handlers, JS URLs), and
 * hands each message to
 * {@link com.halilov.online.notification.EmailService} so the outbox
 * absorbs the inevitable Brevo rate-limit pushback.
 *
 * <p>{@link com.halilov.online.marketing.UnsubscribeController} services
 * the one-click {@code GET /api/marketing/unsubscribe?t=...} link that
 * lands in every email — token-gated so it works without a logged-in
 * session.
 */
package com.halilov.online.marketing;
