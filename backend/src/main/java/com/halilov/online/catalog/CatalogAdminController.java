package com.halilov.online.catalog;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.halilov.online.audit.AuditAction;
import com.halilov.online.audit.AuditService;
import com.halilov.online.common.BulkIdsRequest;
import com.halilov.online.common.BulkResult;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Admin catalog management under {@code /api/admin/catalog}: category
 * CRUD, product CRUD, image upload, stock adjustments, and a CSV
 * export. Audit-trails every mutating call via
 * {@link com.halilov.online.audit.AuditService}.
 */
@RestController
@RequestMapping("/api/admin/catalog")
public class CatalogAdminController {

    private final CatalogService catalog;
    private final AuditService audit;

    public CatalogAdminController(CatalogService catalog, AuditService audit) {
        this.catalog = catalog;
        this.audit = audit;
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.CategoryView createCategory(@Valid @RequestBody CatalogDtos.CategoryUpsert req) {
        CatalogDtos.CategoryView c = catalog.createCategory(req);
        audit.record(AuditAction.CATEGORY_CREATED, "category", c.id(), "קטגוריה נוצרה: " + c.nameHe());
        return c;
    }

    @PutMapping("/categories/{id}")
    public CatalogDtos.CategoryView updateCategory(@PathVariable Long id, @Valid @RequestBody CatalogDtos.CategoryUpsert req) {
        CatalogDtos.CategoryView c = catalog.updateCategory(id, req);
        audit.record(AuditAction.CATEGORY_UPDATED, "category", id, "קטגוריה עודכנה: " + c.nameHe());
        return c;
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        catalog.deleteCategory(id);
        audit.record(AuditAction.CATEGORY_DELETED, "category", id, "קטגוריה נמחקה (#" + id + ")");
    }

    /** Bulk-delete categories. POST (not DELETE-with-body) so the id list
     *  travels in a normal JSON body across every client/proxy. */
    @PostMapping("/categories/bulk-delete")
    public BulkResult bulkDeleteCategories(@Valid @RequestBody BulkIdsRequest req) {
        int deleted = catalog.deleteCategories(req.ids());
        audit.record(AuditAction.CATEGORY_BULK_DELETED, "category", null,
            deleted + " קטגוריות נמחקו בבת אחת", BulkResult.idsMetadata(req.ids()));
        return new BulkResult(deleted, req.ids().size() - deleted);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.ProductView createProduct(@Valid @RequestBody CatalogDtos.ProductUpsert req) {
        CatalogDtos.ProductView p = catalog.createProduct(req);
        audit.record(AuditAction.PRODUCT_CREATED, "product", p.id(), "מוצר חדש: " + p.nameHe() + " (" + p.sku() + ")");
        return p;
    }

    @PutMapping("/products/{id}")
    public CatalogDtos.ProductView updateProduct(@PathVariable Long id, @Valid @RequestBody CatalogDtos.ProductUpsert req) {
        CatalogDtos.ProductView p = catalog.updateProduct(id, req);
        audit.record(AuditAction.PRODUCT_UPDATED, "product", id, "מוצר עודכן: " + p.nameHe() + " (" + p.sku() + ")");
        return p;
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        catalog.deleteProduct(id);
        audit.record(AuditAction.PRODUCT_DELETED, "product", id, "מוצר נמחק (#" + id + ")");
    }

    /** Bulk-delete products. CSV export exists as a pre-delete backup. */
    @PostMapping("/products/bulk-delete")
    public BulkResult bulkDeleteProducts(@Valid @RequestBody BulkIdsRequest req) {
        int deleted = catalog.deleteProducts(req.ids());
        audit.record(AuditAction.PRODUCT_BULK_DELETED, "product", null,
            deleted + " מוצרים נמחקו בבת אחת", BulkResult.idsMetadata(req.ids()));
        return new BulkResult(deleted, req.ids().size() - deleted);
    }

    @GetMapping(value = "/products.csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<String> exportProductsCsv() {
        String csv = catalog.exportProductsCsv();
        String filename = "products-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }

    @PostMapping(value = "/products/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CatalogDtos.ImportResult importProductsCsv(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.BAD_REQUEST, "missing file");
        }
        return catalog.importProductsCsv(file.getInputStream());
    }
}
