package com.halilov.online.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.common.Csv;
import com.halilov.online.notification.StockNotificationService;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catalog reads + admin CRUD for products and categories. Wraps the
 * two repositories so the controllers stay thin (mapping +
 * authorization only) and the rules — slug normalisation, image-URL
 * array maintenance, active filtering, restock-notification fan-out —
 * live in one place.
 *
 * <p>When a product transitions {@code stockQty 0 → >0} the service
 * fires {@link StockNotificationService#triggerForProduct} so anyone
 * waiting on a "notify me" sign-up gets a one-shot email.
 */
@Service
public class CatalogService {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final StockNotificationService stockNotifications;

    public CatalogService(CategoryRepository categories, ProductRepository products,
                          StockNotificationService stockNotifications) {
        this.categories = categories;
        this.products = products;
        this.stockNotifications = stockNotifications;
    }

    public List<CatalogDtos.CategoryView> listCategories() {
        return categories.findAllByOrderBySortOrderAscNameHeAsc()
            .stream().map(CatalogDtos.CategoryView::from).toList();
    }

    public Page<CatalogDtos.ProductView> listPublic(Long categoryId, String q, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        String trimmed = q == null ? null : q.trim();
        boolean hasQuery = trimmed != null && !trimmed.isEmpty();

        Page<Product> p;
        if (hasQuery && categoryId != null) {
            p = products.searchActiveByCategory(categoryId, trimmed, pageable);
        } else if (hasQuery) {
            p = products.searchActive(trimmed, pageable);
        } else if (categoryId != null) {
            p = products.findByActiveTrueAndCategoryId(categoryId, pageable);
        } else {
            p = products.findByActiveTrue(pageable);
        }
        return p.map(CatalogDtos.ProductView::from);
    }

    public CatalogDtos.ProductView getBySlug(String slug) {
        Product p = products.findBySlug(slug)
            .filter(Product::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
        return CatalogDtos.ProductView.from(p);
    }

    // ---- admin ----

    @Transactional
    public CatalogDtos.CategoryView createCategory(CatalogDtos.CategoryUpsert req) {
        if (categories.existsBySlug(req.slug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "slug taken");
        }
        Category c = new Category();
        applyCategory(c, req);
        return CatalogDtos.CategoryView.from(categories.save(c));
    }

    @Transactional
    public CatalogDtos.CategoryView updateCategory(Long id, CatalogDtos.CategoryUpsert req) {
        Category c = categories.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found"));
        if (!c.getSlug().equals(req.slug()) && categories.existsBySlug(req.slug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "slug taken");
        }
        applyCategory(c, req);
        return CatalogDtos.CategoryView.from(c);
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (!categories.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found");
        }
        categories.deleteById(id);
    }

    @Transactional
    public CatalogDtos.ProductView createProduct(CatalogDtos.ProductUpsert req) {
        if (products.existsBySku(req.sku())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "sku taken");
        }
        if (products.existsBySlug(req.slug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "slug taken");
        }
        Product p = new Product();
        applyProduct(p, req);
        return CatalogDtos.ProductView.from(products.save(p));
    }

    @Transactional
    public CatalogDtos.ProductView updateProduct(Long id, CatalogDtos.ProductUpsert req) {
        Product p = products.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
        if (!p.getSku().equals(req.sku()) && products.existsBySku(req.sku())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "sku taken");
        }
        if (!p.getSlug().equals(req.slug()) && products.existsBySlug(req.slug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "slug taken");
        }
        int oldStock = p.getStockQty();
        applyProduct(p, req);
        if (oldStock <= 0 && p.getStockQty() > 0 && p.isActive()) {
            stockNotifications.notifyRestocked(p.getId());
        }
        return CatalogDtos.ProductView.from(p);
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (!products.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found");
        }
        products.deleteById(id);
    }

    /**
     * Delete every product whose id is in {@code ids}. Ids that don't
     * resolve to a row are silently skipped, so the returned count is the
     * number actually removed — the caller audits that, not the request
     * size. One DB round-trip via {@code findAllById} + {@code deleteAll}.
     */
    @Transactional
    public int deleteProducts(List<Long> ids) {
        List<Product> found = products.findAllById(ids);
        products.deleteAll(found);
        return found.size();
    }

    /**
     * Every product, active or hidden, for the admin catalog table. The public
     * {@code /api/products} read filters to active-only, which would make a
     * hidden product vanish from the admin's own list — so admin reads this.
     * Newest first, matching the public list's default ordering.
     */
    @Transactional(readOnly = true)
    public List<CatalogDtos.ProductView> listAllForAdmin() {
        return products.findAll(Sort.by("createdAt").descending())
            .stream().map(CatalogDtos.ProductView::from).toList();
    }

    /**
     * Flip the {@code active} flag on every product in {@code ids}. Ids that
     * don't resolve are skipped; the returned count is how many rows actually
     * changed (already-in-that-state rows are not re-counted), so the audit
     * reflects real effect. Mirrors the single-product active toggle that the
     * edit form performs, with no cache to evict.
     */
    @Transactional
    public int setProductsActive(List<Long> ids, boolean active) {
        List<Product> found = products.findAllById(ids);
        int changed = 0;
        for (Product p : found) {
            if (p.isActive() != active) {
                p.setActive(active);
                changed++;
            }
        }
        return changed;
    }

    /** Bulk sibling of {@link #deleteCategory}. Products keep working;
     *  they just lose the category link, exactly like the single delete. */
    @Transactional
    public int deleteCategories(List<Long> ids) {
        List<Category> found = categories.findAllById(ids);
        categories.deleteAll(found);
        return found.size();
    }

    private void applyCategory(Category c, CatalogDtos.CategoryUpsert req) {
        c.setSlug(req.slug());
        c.setNameHe(req.nameHe());
        c.setParentId(req.parentId());
        c.setSortOrder(req.sortOrder());
    }

    private void applyProduct(Product p, CatalogDtos.ProductUpsert req) {
        p.setSku(req.sku());
        p.setSlug(req.slug());
        p.setNameHe(req.nameHe());
        p.setDescriptionHe(req.descriptionHe());
        p.setCategoryId(req.categoryId());
        p.setPriceAgorot(req.priceAgorot());
        p.setStockQty(req.stockQty());
        p.setImageUrl(req.imageUrl());
        p.setImageUrls(CatalogDtos.joinUrls(req.imageUrls()));
        p.setActive(req.active());
    }

    // ---- CSV import/export ----

    private static final String[] PRODUCT_CSV_HEADER = {
        "sku", "slug", "nameHe", "descriptionHe", "categorySlug",
        "priceAgorot", "stockQty", "imageUrl", "active"
    };

    @Transactional(readOnly = true)
    public String exportProductsCsv() {
        Map<Long, String> catSlugById = new HashMap<>();
        for (Category c : categories.findAllByOrderBySortOrderAscNameHeAsc()) {
            catSlugById.put(c.getId(), c.getSlug());
        }
        StringBuilder out = new StringBuilder(Csv.BOM);
        out.append(Csv.row((Object[]) PRODUCT_CSV_HEADER));
        for (Product p : products.findAll(Sort.by("sku"))) {
            out.append(Csv.row(
                p.getSku(),
                p.getSlug(),
                p.getNameHe(),
                p.getDescriptionHe() == null ? "" : p.getDescriptionHe(),
                p.getCategoryId() != null ? catSlugById.getOrDefault(p.getCategoryId(), "") : "",
                p.getPriceAgorot(),
                p.getStockQty(),
                p.getImageUrl() == null ? "" : p.getImageUrl(),
                p.isActive()
            ));
        }
        return out.toString();
    }

    @Transactional
    public CatalogDtos.ImportResult importProductsCsv(InputStream in) {
        List<List<String>> rows;
        try {
            rows = Csv.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty file");
        }

        Map<String, Integer> idx = new HashMap<>();
        List<String> header = rows.get(0);
        for (int i = 0; i < header.size(); i++) idx.put(header.get(i).trim(), i);
        for (String required : new String[]{"sku", "slug", "nameHe", "priceAgorot"}) {
            if (!idx.containsKey(required)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "missing required column: " + required);
            }
        }

        Map<String, Long> catIdBySlug = new HashMap<>();
        for (Category c : categories.findAllByOrderBySortOrderAscNameHeAsc()) {
            catIdBySlug.put(c.getSlug(), c.getId());
        }
        Map<String, Product> productBySku = new HashMap<>();
        Map<String, Product> productBySlug = new HashMap<>();
        for (Product p : products.findAll()) {
            productBySku.put(p.getSku(), p);
            productBySlug.put(p.getSlug(), p);
        }

        int created = 0;
        int updated = 0;
        List<CatalogDtos.ImportRowError> errors = new ArrayList<>();
        Set<Long> restockedProductIds = new HashSet<>();

        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            int lineNo = r + 1;
            String sku = cell(row, idx, "sku");
            try {
                if (sku.isEmpty()) {
                    errors.add(new CatalogDtos.ImportRowError(lineNo, sku, "sku empty"));
                    continue;
                }
                String slug = cell(row, idx, "slug");
                String nameHe = cell(row, idx, "nameHe");
                if (slug.isEmpty() || nameHe.isEmpty()) {
                    errors.add(new CatalogDtos.ImportRowError(lineNo, sku, "slug/nameHe empty"));
                    continue;
                }
                int priceAgorot;
                int stockQty;
                try {
                    priceAgorot = Integer.parseInt(cell(row, idx, "priceAgorot"));
                    stockQty = idx.containsKey("stockQty") && !cell(row, idx, "stockQty").isEmpty()
                        ? Integer.parseInt(cell(row, idx, "stockQty")) : 0;
                } catch (NumberFormatException nfe) {
                    errors.add(new CatalogDtos.ImportRowError(lineNo, sku,
                        "priceAgorot/stockQty must be integers"));
                    continue;
                }
                if (priceAgorot < 0 || stockQty < 0) {
                    errors.add(new CatalogDtos.ImportRowError(lineNo, sku, "negative price/stock"));
                    continue;
                }

                Long categoryId = null;
                String categorySlug = cell(row, idx, "categorySlug");
                if (!categorySlug.isEmpty()) {
                    categoryId = catIdBySlug.get(categorySlug);
                    if (categoryId == null) {
                        errors.add(new CatalogDtos.ImportRowError(lineNo, sku,
                            "unknown categorySlug: " + categorySlug));
                        continue;
                    }
                }

                String descriptionHe = cell(row, idx, "descriptionHe");
                String imageUrl = cell(row, idx, "imageUrl");
                boolean active = parseBool(cell(row, idx, "active"), true);

                Product existing = productBySku.get(sku);
                Product slugOwner = productBySlug.get(slug);
                if (slugOwner != null && existing != null && !slugOwner.getId().equals(existing.getId())) {
                    errors.add(new CatalogDtos.ImportRowError(lineNo, sku, "slug already taken: " + slug));
                    continue;
                }
                if (existing == null && slugOwner != null) {
                    errors.add(new CatalogDtos.ImportRowError(lineNo, sku, "slug already taken: " + slug));
                    continue;
                }

                Product p = existing != null ? existing : new Product();
                int oldStock = existing != null ? existing.getStockQty() : 0;
                p.setSku(sku);
                p.setSlug(slug);
                p.setNameHe(nameHe);
                p.setDescriptionHe(descriptionHe.isEmpty() ? null : descriptionHe);
                p.setCategoryId(categoryId);
                p.setPriceAgorot(priceAgorot);
                p.setStockQty(stockQty);
                p.setImageUrl(imageUrl.isEmpty() ? null : imageUrl);
                p.setActive(active);
                Product saved = products.save(p);
                productBySku.put(sku, saved);
                productBySlug.put(slug, saved);
                if (existing == null) {
                    created++;
                } else {
                    updated++;
                    if (oldStock <= 0 && saved.getStockQty() > 0 && saved.isActive()) {
                        restockedProductIds.add(saved.getId());
                    }
                }
            } catch (RuntimeException ex) {
                errors.add(new CatalogDtos.ImportRowError(lineNo, sku,
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
            }
        }

        for (Long pid : restockedProductIds) {
            stockNotifications.notifyRestocked(pid);
        }

        return new CatalogDtos.ImportResult(created, updated, rows.size() - 1, errors);
    }

    private static String cell(List<String> row, Map<String, Integer> idx, String col) {
        Integer i = idx.get(col);
        if (i == null || i >= row.size()) return "";
        return row.get(i) == null ? "" : row.get(i).trim();
    }

    private static boolean parseBool(String s, boolean dflt) {
        if (s == null || s.isEmpty()) return dflt;
        String v = s.trim().toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("y");
    }
}
