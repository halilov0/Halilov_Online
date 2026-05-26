package com.halilov.online.auth.totp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps the {@code app.admin.trustedIps} CSV property. An ADMIN with TOTP
 * enabled bypasses the second-factor step only when their login originates
 * from one of these IPs (your home / office). Anyone hitting the admin from
 * elsewhere has to present the TOTP code, even with valid password.
 * <p>
 * Each entry is either a bare IP ({@code 1.2.3.4}, {@code 2a00:...:20ed})
 * or a CIDR block ({@code 10.0.0.0/8}, {@code 2a00:a041:e9c4:f400::/64}).
 * CIDR is the right choice for IPv6: SLAAC privacy extensions rotate the
 * host portion of the address, so pinning a bare IPv6 stops matching after
 * the OS picks a new temporary address. Pin the /64 prefix instead.
 * Empty list = no IP is trusted = 2FA always required (when enabled).
 */
@Component
public class TrustedIpService {

    private static final Logger log = LoggerFactory.getLogger(TrustedIpService.class);

    private final List<CidrRule> rules;

    public TrustedIpService(@Value("${app.admin.trustedIps:}") String raw) {
        List<CidrRule> parsed = new ArrayList<>();
        if (raw != null && !raw.isBlank()) {
            for (String s : raw.split(",")) {
                String t = s.trim();
                if (t.isEmpty()) continue;
                CidrRule rule = CidrRule.parse(t);
                if (rule != null) {
                    parsed.add(rule);
                } else {
                    log.warn("Ignoring malformed ADMIN_TRUSTED_IPS entry: {}", t);
                }
            }
        }
        this.rules = Collections.unmodifiableList(parsed);
    }

    public boolean isTrusted(String ip) {
        if (ip == null || ip.isBlank() || rules.isEmpty()) return false;
        InetAddress target;
        try {
            target = InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            return false;
        }
        byte[] bytes = target.getAddress();
        for (CidrRule rule : rules) {
            if (rule.matches(bytes)) return true;
        }
        return false;
    }

    private record CidrRule(byte[] prefixBytes, int prefixLen) {

        static CidrRule parse(String entry) {
            String addr = entry;
            int len = -1;
            int slash = entry.indexOf('/');
            if (slash >= 0) {
                addr = entry.substring(0, slash);
                try {
                    len = Integer.parseInt(entry.substring(slash + 1));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            InetAddress ia;
            try {
                ia = InetAddress.getByName(addr);
            } catch (UnknownHostException e) {
                return null;
            }
            byte[] bytes = ia.getAddress();
            int maxBits = bytes.length * 8;
            if (len < 0) len = maxBits;
            if (len > maxBits) return null;
            return new CidrRule(bytes, len);
        }

        boolean matches(byte[] target) {
            if (target.length != prefixBytes.length) return false;
            int fullBytes = prefixLen / 8;
            int remainingBits = prefixLen % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (target[i] != prefixBytes[i]) return false;
            }
            if (remainingBits == 0) return true;
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (target[fullBytes] & mask) == (prefixBytes[fullBytes] & mask);
        }
    }
}
