package com.halilov.online.favorites;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Server-backed favorites (wishlist) for authenticated users under
 * {@code /api/favorites}. Mirrors the cart contract:
 *
 * <ul>
 *   <li>{@code GET /} — current canonical set.</li>
 *   <li>{@code PUT /} — replace the favorites wholesale (continuous sync
 *       from the SPA, debounced).</li>
 *   <li>{@code POST /merge} — union incoming with existing, used once at
 *       login to fold the guest's local wishlist into the account.</li>
 *   <li>{@code DELETE /} — clear everything.</li>
 * </ul>
 *
 * <p>Guests don't hit any of these — their wishlist is entirely
 * browser-side until they sign in.
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favorites;

    public FavoriteController(FavoriteService favorites) {
        this.favorites = favorites;
    }

    @GetMapping
    public FavoriteDtos.FavoritesResponse get(Authentication auth) {
        return favorites.getFavorites(requireEmail(auth));
    }

    @PutMapping
    public FavoriteDtos.FavoritesResponse replace(Authentication auth,
                                                  @Valid @RequestBody FavoriteDtos.FavoritesReplaceRequest req) {
        return favorites.replaceFavorites(requireEmail(auth), req.productIds());
    }

    @PostMapping("/merge")
    public FavoriteDtos.FavoritesResponse merge(Authentication auth,
                                                @Valid @RequestBody FavoriteDtos.FavoritesReplaceRequest req) {
        return favorites.mergeFavorites(requireEmail(auth), req.productIds());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(Authentication auth) {
        favorites.clearFavorites(requireEmail(auth));
    }

    private static String requireEmail(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no session");
        }
        return auth.getName();
    }
}
