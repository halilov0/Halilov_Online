package com.halilov.online.catalog;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
public class SitemapController {

    private static final String BASE_URL = "https://halilov.co.il";

    private static final List<String> STATIC_PATHS = List.of(
        "/",
        "/about",
        "/shipping",
        "/returns",
        "/faq",
        "/contact",
        "/terms",
        "/privacy",
        "/accessibility"
    );

    private final ProductRepository products;

    public SitemapController(ProductRepository products) {
        this.products = products;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        for (String path : STATIC_PATHS) {
            appendUrl(xml, BASE_URL + path, null);
        }

        for (Product p : products.findAll()) {
            if (!p.isActive()) continue;
            appendUrl(xml, BASE_URL + "/p/" + p.getSlug(), p.getUpdatedAt());
        }

        xml.append("</urlset>\n");

        return ResponseEntity.ok()
            .header("Cache-Control", "public, max-age=3600")
            .body(xml.toString());
    }

    private static void appendUrl(StringBuilder xml, String loc, Instant lastmod) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(escape(loc)).append("</loc>\n");
        if (lastmod != null) {
            xml.append("    <lastmod>")
               .append(DateTimeFormatter.ISO_INSTANT.format(lastmod))
               .append("</lastmod>\n");
        }
        xml.append("  </url>\n");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
