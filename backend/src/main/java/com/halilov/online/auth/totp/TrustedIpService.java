package com.halilov.online.auth.totp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Wraps the {@code app.admin.trustedIps} CSV property. An ADMIN with TOTP
 * enabled bypasses the second-factor step only when their login originates
 * from one of these IPs (your home / office). Anyone hitting the admin from
 * elsewhere has to present the TOTP code, even with valid password.
 * <p>
 * Plain string equality on the resolved client IP — no CIDR matching to
 * keep this simple. Set the env var to the IP shown by ifconfig.me.
 * Empty list = no IP is trusted = 2FA always required (when enabled).
 */
@Component
public class TrustedIpService {

    private final Set<String> trusted;

    public TrustedIpService(@Value("${app.admin.trustedIps:}") String raw) {
        Set<String> ips = new HashSet<>();
        if (raw != null && !raw.isBlank()) {
            for (String s : raw.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) ips.add(t);
            }
        }
        this.trusted = Collections.unmodifiableSet(ips);
    }

    public boolean isTrusted(String ip) {
        return ip != null && trusted.contains(ip);
    }
}
