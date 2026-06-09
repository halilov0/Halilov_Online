package com.halilov.online.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the originating client IP for a request that arrives through the
 * Cloudflare edge + nginx reverse proxy. {@code request.getRemoteAddr()} alone
 * returns the proxy's address, not the real visitor — so prefer the
 * {@code CF-Connecting-IP} header Cloudflare sets, then the first hop of
 * {@code X-Forwarded-For}, and only fall back to the socket address.
 *
 * <p>Single source for both audit logging and the admin trusted-IP check, so
 * the two can never disagree on "who is this request from".
 */
public final class ClientIp {

    private ClientIp() {}

    public static String resolve(HttpServletRequest req) {
        if (req == null) return null;
        String h = req.getHeader("CF-Connecting-IP");
        if (h != null && !h.isBlank()) return h.trim();
        h = req.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) {
            int comma = h.indexOf(',');
            return (comma >= 0 ? h.substring(0, comma) : h).trim();
        }
        return req.getRemoteAddr();
    }
}
