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
    public List<CartDtos.CartLineView> getCart(String email) {
        Long userId = requireUser(email).getId();
        return resolve(cartLines.findByUserId(userId));
    }

    /** Replace the user's cart wholesale — used for continuous sync and final
     *  logout flush. Lines for missing/inactive products or qty<=0 are dropped;
     *  qty is clamped to current stock. */
    @Transactional
    public List<CartDtos.CartLineView> replaceCart(String email, List<CartDtos.CartUpsertItem> incoming) {
        Long userId = requireUser(email).getId();
        Map<Long, Integer> desired = collapseByProduct(incoming);
        cartLines.deleteByUserId(userId);
        cartLines.flush();
        persist(userId, desired);
        return resolve(cartLines.findByUserId(userId));
    }

    /** Merge incoming lines with the existing DB cart — duplicate productIds sum,
     *  total per line is clamped to stock and MAX_QTY_PER_LINE. */
    @Transactional
    public List<CartDtos.CartLineView> mergeCart(String email, List<CartDtos.CartUpsertItem> incoming) {
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
        persist(userId, sum);
        return resolve(cartLines.findByUserId(userId));
    }

    @Transactional
    public void clearCart(String email) {
        Long userId = requireUser(email).getId();
        cartLines.deleteByUserId(userId);
    }

    // ---------- helpers ----------

    private void persist(Long userId, Map<Long, Integer> desired) {
        if (desired.isEmpty()) return;
        Map<Long, Product> byId = new HashMap<>();
        for (Product p : products.findAllById(desired.keySet())) {
            byId.put(p.getId(), p);
        }
        for (Map.Entry<Long, Integer> e : desired.entrySet()) {
            Product p = byId.get(e.getKey());
            if (p == null || !p.isActive()) continue;
            int qty = Math.min(MAX_QTY_PER_LINE, Math.min(p.getStockQty(), e.getValue()));
            if (qty <= 0) continue;
            cartLines.save(new CartLine(userId, p.getId(), qty));
        }
    }

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
