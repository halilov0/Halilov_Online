/**
 * Server-backed favorites (wishlist) for authenticated users.
 *
 * <p>{@code favorites} is a flat (user_id, product_id) table — no
 * quantity, no ordering. The frontend treats the server as source of
 * truth and PUTs the full set on every change (debounced). Two
 * operations:
 * <ul>
 *   <li>{@link com.halilov.online.favorites.FavoriteService#replaceFavorites} —
 *       wholesale overwrite used by continuous sync and logout flush.</li>
 *   <li>{@link com.halilov.online.favorites.FavoriteService#mergeFavorites} —
 *       union incoming with existing rows. Used at login to merge a
 *       guest's local wishlist into the server set.</li>
 * </ul>
 *
 * <p>Both operations drop rows for missing or inactive products and
 * return the resolved view back to the client, so the SPA can show a
 * notice instead of having favorites vanish silently.
 *
 * <p>Guests have no rows here — their wishlist is entirely
 * {@code localStorage} on the SPA side.
 */
package com.halilov.online.favorites;
