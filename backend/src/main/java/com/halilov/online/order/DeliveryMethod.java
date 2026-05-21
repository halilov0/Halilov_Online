package com.halilov.online.order;

public enum DeliveryMethod {
    /** Courier delivers to the buyer's address. Flat rate, free above threshold. */
    COURIER
    // Pickup-point options will be added when a real courier API is integrated —
    // the points come from the courier's network (HFD/דואר/etc.), not from us.
}
