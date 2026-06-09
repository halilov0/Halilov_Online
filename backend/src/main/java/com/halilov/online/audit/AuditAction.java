package com.halilov.online.audit;

/**
 * Stable identifiers for actions persisted to audit_log. Keep values as
 * plain strings (no enum DB binding) so renaming the Java symbol doesn't
 * orphan historical rows — match by string only when filtering.
 */
public final class AuditAction {
    private AuditAction() {}

    // auth
    public static final String USER_REGISTER       = "USER_REGISTER";
    public static final String USER_LOGIN          = "USER_LOGIN";
    public static final String USER_LOGIN_FAILED   = "USER_LOGIN_FAILED";
    public static final String USER_LOGIN_LOCKED   = "USER_LOGIN_LOCKED";
    public static final String USER_LOGIN_2FA_CHALLENGE = "USER_LOGIN_2FA_CHALLENGE";
    public static final String USER_LOGIN_2FA_FAILED    = "USER_LOGIN_2FA_FAILED";
    public static final String USER_TOTP_ENROLLED       = "USER_TOTP_ENROLLED";
    public static final String USER_TOTP_DISABLED       = "USER_TOTP_DISABLED";

    // orders (customer)
    public static final String ORDER_PLACED        = "ORDER_PLACED";
    public static final String ORDER_CANCELLED     = "ORDER_CANCELLED";

    // orders (admin)
    public static final String ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED";
    public static final String ORDER_REFUNDED      = "ORDER_REFUNDED";

    // payments (gateway-driven, no human actor)
    public static final String PAYMENT_CAPTURED    = "PAYMENT_CAPTURED";
    // receipt marked manually by an admin (lean launch — no automated GI API yet)
    public static final String RECEIPT_ISSUED_MANUAL = "RECEIPT_ISSUED_MANUAL";

    // catalog (admin)
    public static final String PRODUCT_CREATED     = "PRODUCT_CREATED";
    public static final String PRODUCT_UPDATED     = "PRODUCT_UPDATED";
    public static final String PRODUCT_DELETED     = "PRODUCT_DELETED";
    public static final String PRODUCT_BULK_DELETED = "PRODUCT_BULK_DELETED";
    public static final String PRODUCT_BULK_STATUS = "PRODUCT_BULK_STATUS";
    public static final String CATEGORY_CREATED    = "CATEGORY_CREATED";
    public static final String CATEGORY_UPDATED    = "CATEGORY_UPDATED";
    public static final String CATEGORY_DELETED    = "CATEGORY_DELETED";
    public static final String CATEGORY_BULK_DELETED = "CATEGORY_BULK_DELETED";

    // coupons (admin)
    public static final String COUPON_CREATED      = "COUPON_CREATED";
    public static final String COUPON_UPDATED      = "COUPON_UPDATED";
    public static final String COUPON_DELETED      = "COUPON_DELETED";
    public static final String COUPON_BULK_DELETED = "COUPON_BULK_DELETED";

    // marketing (admin)
    public static final String MARKETING_CAMPAIGN_SENT = "MARKETING_CAMPAIGN_SENT";

    // account (customer self-serve)
    public static final String ACCOUNT_PROFILE_UPDATED   = "ACCOUNT_PROFILE_UPDATED";
    public static final String ACCOUNT_PASSWORD_CHANGED  = "ACCOUNT_PASSWORD_CHANGED";
    public static final String ACCOUNT_MARKETING_CHANGED = "ACCOUNT_MARKETING_CHANGED";
    public static final String ACCOUNT_ADDRESS_SAVED     = "ACCOUNT_ADDRESS_SAVED";
    public static final String ACCOUNT_ADDRESS_DELETED   = "ACCOUNT_ADDRESS_DELETED";

    // admin user management (admin → other user)
    public static final String USER_ENABLED        = "USER_ENABLED";
    public static final String USER_DISABLED       = "USER_DISABLED";
    public static final String USER_BULK_ENABLED   = "USER_BULK_ENABLED";
    public static final String USER_BULK_DISABLED  = "USER_BULK_DISABLED";
    public static final String USER_FORCE_LOGOUT   = "USER_FORCE_LOGOUT";

    // audit log maintenance (admin)
    public static final String AUDIT_LOG_PURGED    = "AUDIT_LOG_PURGED";

    // password reset
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED";
}
