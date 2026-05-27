/**
 * Server-backed cart for authenticated users.
 *
 * <p>{@code cart_lines} is a flat (user_id, product_id, quantity) table.
 * The frontend treats the server as source of truth and PUTs the full
 * cart on every change (debounced). Two operations:
 * <ul>
 *   <li>{@link com.halilov.online.cart.CartService#replaceCart} — wholesale
 *       overwrite used by continuous sync and logout flush.</li>
 *   <li>{@link com.halilov.online.cart.CartService#mergeCart} — sum
 *       incoming quantities into the existing cart. Used at login to
 *       merge a guest's local cart into the server cart.</li>
 * </ul>
 *
 * <p>Both operations:
 * <ul>
 *   <li>Drop lines for missing or inactive products.</li>
 *   <li>Clamp quantities to current stock and to a soft per-line ceiling
 *       (matches the legacy localStorage cap).</li>
 *   <li>Return the resolved view back to the client, so the UI can
 *       reconcile against any clamps it didn't make itself.</li>
 * </ul>
 *
 * <p>Guests have no rows here — their cart is entirely
 * {@code localStorage} on the SPA side.
 */
package com.halilov.online.cart;
