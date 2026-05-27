/**
 * Product catalog: products, categories, search, sitemap.
 *
 * <p>Two controllers split by audience:
 * <ul>
 *   <li>{@link com.halilov.online.catalog.CatalogPublicController} —
 *       customer-facing read endpoints (list, search, category browse,
 *       product detail). Cacheable, no auth required.</li>
 *   <li>{@link com.halilov.online.catalog.CatalogAdminController} —
 *       admin CRUD for products and categories, stock adjustments,
 *       (de)activation.</li>
 * </ul>
 *
 * <p>{@link com.halilov.online.catalog.SitemapController} emits
 * {@code /sitemap.xml} on demand from active products + the static
 * top-level routes — driven by Google Search Console's daily inspection
 * routine.
 *
 * <p>Image URLs on {@link com.halilov.online.catalog.Product} are stored
 * as a JSON array; the storage backend (local disk or R2) is owned by
 * {@link com.halilov.online.media}.
 */
package com.halilov.online.catalog;
