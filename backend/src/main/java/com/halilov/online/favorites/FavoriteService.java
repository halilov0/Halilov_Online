package com.halilov.online.favorites;

import com.halilov.online.catalog.Product;
import com.halilov.online.catalog.ProductRepository;
import com.halilov.online.user.User;
import com.halilov.online.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FavoriteService {

    private final FavoriteRepository favorites;
    private final ProductRepository products;
    private final UserRepository users;

    public FavoriteService(FavoriteRepository favorites, ProductRepository products, UserRepository users) {
        this.favorites = favorites;
        this.products = products;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public FavoriteDtos.FavoritesResponse getFavorites(String email) {
        Long userId = requireUser(email).getId();
        return resolveWithAdjustments(favorites.findByUserId(userId));
    }

    /** Replace the user's favorites wholesale — used for continuous sync
     *  and final logout flush. Missing/inactive products are dropped and
     *  reported. */
    @Transactional
    public FavoriteDtos.FavoritesResponse replaceFavorites(String email, List<Long> incoming) {
        Long userId = requireUser(email).getId();
        Set<Long> desired = collapse(incoming);
        favorites.deleteByUserId(userId);
        favorites.flush();
        List<FavoriteDtos.FavoriteAdjustment> adjustments = persist(userId, desired);
        return new FavoriteDtos.FavoritesResponse(currentIds(userId), adjustments);
    }

    /** Merge incoming productIds with the existing DB favorites — used
     *  once at login to fold a guest's local wishlist into the account. */
    @Transactional
    public FavoriteDtos.FavoritesResponse mergeFavorites(String email, List<Long> incoming) {
        Long userId = requireUser(email).getId();
        Set<Long> union = new LinkedHashSet<>();
        for (Favorite existing : favorites.findByUserId(userId)) {
            union.add(existing.getProductId());
        }
        union.addAll(collapse(incoming));
        favorites.deleteByUserId(userId);
        favorites.flush();
        List<FavoriteDtos.FavoriteAdjustment> adjustments = persist(userId, union);
        return new FavoriteDtos.FavoritesResponse(currentIds(userId), adjustments);
    }

    @Transactional
    public void clearFavorites(String email) {
        Long userId = requireUser(email).getId();
        favorites.deleteByUserId(userId);
    }

    // ---------- helpers ----------

    /** Persist desired ids, dropping rows for missing/inactive products
     *  and reporting every drop so the client can tell the user. */
    private List<FavoriteDtos.FavoriteAdjustment> persist(Long userId, Set<Long> desired) {
        if (desired.isEmpty()) return List.of();
        Map<Long, Product> byId = new HashMap<>();
        for (Product p : products.findAllById(desired)) {
            byId.put(p.getId(), p);
        }
        List<FavoriteDtos.FavoriteAdjustment> adjustments = new ArrayList<>();
        for (Long productId : desired) {
            Product p = byId.get(productId);
            if (p == null) {
                adjustments.add(removed(productId, null));
                continue;
            }
            if (!p.isActive()) {
                adjustments.add(removed(p.getId(), p.getNameHe()));
                continue;
            }
            favorites.save(new Favorite(userId, p.getId()));
        }
        return adjustments;
    }

    /** Current stored favorite ids, filtered to active products (silently;
     *  callers that wrote them already reported the drops). */
    private List<Long> currentIds(Long userId) {
        List<Favorite> rows = favorites.findByUserId(userId);
        if (rows.isEmpty()) return List.of();
        Set<Long> ids = new LinkedHashSet<>();
        for (Favorite r : rows) ids.add(r.getProductId());
        List<Long> out = new ArrayList<>(ids.size());
        for (Product p : products.findAllById(ids)) {
            if (p.isActive()) out.add(p.getId());
        }
        return out;
    }

    /** Like {@link #currentIds} but reports stored rows that have since
     *  gone inactive/missing — so a plain read (focus refresh) can tell
     *  the user why a favorite disappeared. No mutation here; the dead
     *  row is left in place and the next write prunes it. */
    private FavoriteDtos.FavoritesResponse resolveWithAdjustments(List<Favorite> rows) {
        if (rows.isEmpty()) return FavoriteDtos.FavoritesResponse.of(List.of());
        Set<Long> stored = new LinkedHashSet<>();
        for (Favorite r : rows) stored.add(r.getProductId());
        Map<Long, Product> byId = new HashMap<>();
        for (Product p : products.findAllById(stored)) {
            byId.put(p.getId(), p);
        }
        List<Long> out = new ArrayList<>();
        List<FavoriteDtos.FavoriteAdjustment> adjustments = new ArrayList<>();
        for (Long productId : stored) {
            Product p = byId.get(productId);
            if (p == null) {
                adjustments.add(removed(productId, null));
            } else if (!p.isActive()) {
                adjustments.add(removed(p.getId(), p.getNameHe()));
            } else {
                out.add(p.getId());
            }
        }
        return new FavoriteDtos.FavoritesResponse(out, adjustments);
    }

    private static FavoriteDtos.FavoriteAdjustment removed(Long productId, String nameHe) {
        return new FavoriteDtos.FavoriteAdjustment(productId, nameHe, FavoriteDtos.AdjustmentType.REMOVED);
    }

    private static Set<Long> collapse(List<Long> ids) {
        Set<Long> out = new LinkedHashSet<>();
        if (ids == null) return out;
        for (Long id : ids) {
            if (id != null) out.add(id);
        }
        return out;
    }

    private User requireUser(String email) {
        return users.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no session"));
    }
}
