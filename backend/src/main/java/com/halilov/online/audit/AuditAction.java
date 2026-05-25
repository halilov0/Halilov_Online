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

    // orders (customer)
    public static final String ORDER_PLACED        = "ORDER_PLACED";
    public static final String ORDER_CANCELLED     = "ORDER_CANCELLED";

    // orders (admin)
    public static final String ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED";
    public static final String ORDER_REFUNDED      = "ORDER_REFUNDED";

    // catalog (admin)
    public static final String PRODUCT_CREATED     = "PRODUCT_CREATED";
    public static final String PRODUCT_UPDATED     = "PRODUCT_UPDATED";
    public static final String PRODUCT_DELETED     = "PRODUCT_DELETED";
    public static final String CATEGORY_CREATED    = "CATEGORY_CREATED";
    public static final String CATEGORY_UPDATED    = "CATEGORY_UPDATED";
    public static final String CATEGORY_DELETED    = "CATEGORY_DELETED";

    // coupons (admin)
    public static final String COUPON_CREATED      = "COUPON_CREATED";
    public static final String COUPON_UPDATED      = "COUPON_UPDATED";
    public static final String COUPON_DELETED      = "COUPON_DELETED";

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
    public static final String USER_FORCE_LOGOUT   = "USER_FORCE_LOGOUT";
}
