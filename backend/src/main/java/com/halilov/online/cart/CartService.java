package com.halilov.online.cart;

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
import java.util.List;
import java.util.Map;

@Service
public class CartService {

    /** Soft per-line ceiling matching the legacy localStorage cap; stock can be lower. */
    private static final int MAX_QTY_PER_LINE = 99;

    private final CartLineRepository cartLines;
    private final ProductRepository products;
    private final UserRepository users;

    public CartService(CartLineRepository cartLines, ProductRepository products, UserRepository users) {
        this.cartLines = cartLines;
        this.products = products;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public CartDtos.CartResponse getCart(String email) {
        Long userId = requireUser(email).getId();
        return resolveWithAdjustments(cartLines.findByUserId(userId));
    }

    /** Replace the user's cart wholesale — used for continuous sync and final
     *  logout flush. Lines for missing/inactive products or qty<=0 are dropped;
     *  qty is clamped to current stock. Returns the canonical cart plus any
     *  clamps/removals so the client can tell the user instead of changing the
     *  cart silently. */
    @Transactional
    public CartDtos.CartResponse replaceCart(String email, List<CartDtos.CartUpsertItem> incoming) {
        Long userId = requireUser(email).getId();
        Map<Long, Integer> desired = collapseByProduct(incoming);
        cartLines.deleteByUserId(userId);
        cartLines.flush();
        List<CartDtos.CartAdjustment> adjustments = persist(userId, desired);
        return new CartDtos.CartResponse(resolve(cartLines.findByUserId(userId)), adjustments);
    }

    /** Merge incoming lines with the existing DB cart — duplicate productIds sum,
     *  total per line is clamped to stock and MAX_QTY_PER_LINE. Returns the
     *  canonical cart plus any clamps/removals. */
    @Transactional
    public CartDtos.CartResponse mergeCart(String email, List<CartDtos.CartUpsertItem> incoming) {
        Long userId = requireUser(email).getId();
        Map<Long, Integer> sum = new HashMap<>();
        for (CartLine existing : cartLines.findByUserId(userId)) {
            sum.merge(existing.getProductId(), existing.getQuantity(), Integer::sum);
        }
        for (Map.Entry<Long, Integer> e : collapseByProduct(incoming).entrySet()) {
            sum.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        cartLines.deleteByUserId(userId);
        cartLines.flush();
        List<CartDtos.CartAdjustment> adjustments = persist(userId, sum);
        return new CartDtos.CartResponse(resolve(cartLines.findByUserId(userId)), adjustments);
    }

    @Transactional
    public void clearCart(String email) {
        Long userId = requireUser(email).getId();
        cartLines.deleteByUserId(userId);
    }

    // ---------- helpers ----------

    /** Persist the desired quantities, dropping/clamping against the catalog,
     *  and report every change so the client can surface it. */
    private List<CartDtos.CartAdjustment> persist(Long userId, Map<Long, Integer> desired) {
        if (desired.isEmpty()) return List.of();
        Map<Long, Product> byId = new HashMap<>();
        for (Product p : products.findAllById(desired.keySet())) {
            byId.put(p.getId(), p);
        }
        List<CartDtos.CartAdjustment> adjustments = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : desired.entrySet()) {
            int requested = e.getValue();
            Product p = byId.get(e.getKey());
            if (p == null) {
                adjustments.add(removed(e.getKey(), null, requested));
                continue;
            }
            if (!p.isActive()) {
                adjustments.add(removed(p.getId(), p.getNameHe(), requested));
                continue;
            }
            int qty = Math.min(MAX_QTY_PER_LINE, Math.min(p.getStockQty(), requested));
            if (qty <= 0) {
                adjustments.add(removed(p.getId(), p.getNameHe(), requested));
                continue;
            }
            if (qty < requested) {
                adjustments.add(new CartDtos.CartAdjustment(
                    p.getId(), p.getNameHe(), CartDtos.AdjustmentType.CLAMPED, requested, qty));
            }
            cartLines.save(new CartLine(userId, p.getId(), qty));
        }
        return adjustments;
    }

    /** View of the stored rows, dropping inactive/missing products silently —
     *  used after a persist (which already reported the drops). */
    private List<CartDtos.CartLineView> resolve(List<CartLine> rows) {
        if (rows.isEmpty()) return List.of();
        Map<Long, Integer> qtyByProduct = new HashMap<>();
        for (CartLine r : rows) qtyByProduct.put(r.getProductId(), r.getQuantity());
        List<CartDtos.CartLineView> out = new ArrayList<>(rows.size());
        for (Product p : products.findAllById(qtyByProduct.keySet())) {
            if (!p.isActive()) continue;
            Integer q = qtyByProduct.get(p.getId());
            if (q == null || q <= 0) continue;
            out.add(CartDtos.CartLineView.from(p, q));
        }
        return out;
    }

    /** Like {@link #resolve} but also reports which stored lines were dropped
     *  (product gone inactive/deleted since they were added), so a plain read —
     *  e.g. the focus refresh — can tell the user why a line disappeared. No
     *  clamping here: a read never changes stored quantities. The dead row is
     *  left in place; the next write prunes it. */
    private CartDtos.CartResponse resolveWithAdjustments(List<CartLine> rows) {
        if (rows.isEmpty()) return CartDtos.CartResponse.of(List.of());
        Map<Long, Integer> qtyByProduct = new HashMap<>();
        for (CartLine r : rows) qtyByProduct.put(r.getProductId(), r.getQuantity());
        Map<Long, Product> byId = new HashMap<>();
        for (Product p : products.findAllById(qtyByProduct.keySet())) {
            byId.put(p.getId(), p);
        }
        List<CartDtos.CartLineView> out = new ArrayList<>(rows.size());
        List<CartDtos.CartAdjustment> adjustments = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : qtyByProduct.entrySet()) {
            int q = e.getValue();
            Product p = byId.get(e.getKey());
            if (p == null) {
                adjustments.add(removed(e.getKey(), null, q));
            } else if (!p.isActive()) {
                adjustments.add(removed(p.getId(), p.getNameHe(), q));
            } else if (q > 0) {
                out.add(CartDtos.CartLineView.from(p, q));
            }
        }
        return new CartDtos.CartResponse(out, adjustments);
    }

    private static CartDtos.CartAdjustment removed(Long productId, String nameHe, int requested) {
        return new CartDtos.CartAdjustment(
            productId, nameHe, CartDtos.AdjustmentType.REMOVED, requested, 0);
    }

    private static Map<Long, Integer> collapseByProduct(List<CartDtos.CartUpsertItem> items) {
        Map<Long, Integer> m = new HashMap<>();
        if (items == null) return m;
        for (CartDtos.CartUpsertItem it : items) {
            if (it == null || it.productId() == null || it.quantity() <= 0) continue;
            m.merge(it.productId(), it.quantity(), Integer::sum);
        }
        return m;
    }

    private User requireUser(String email) {
        return users.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no session"));
    }
}
