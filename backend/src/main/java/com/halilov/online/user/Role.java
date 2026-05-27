package com.halilov.online.user;

/**
 * Coarse-grained authorization role. Mapped into Spring Security as
 * {@code ROLE_CUSTOMER} / {@code ROLE_ADMIN}.
 *
 * <p>{@code ADMIN} is the only role that ever traverses the 2FA gate
 * (when TOTP is enrolled and the request IP isn't trusted). Customers
 * never see 2FA.
 */
public enum Role {
    CUSTOMER, ADMIN
}
