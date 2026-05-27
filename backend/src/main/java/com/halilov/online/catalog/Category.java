package com.halilov.online.catalog;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * The {@code categories} table. {@code parent_id} is nullable — a
 * shallow two-level hierarchy is enough for the current shop, but the
 * column accepts deeper nesting if it's ever needed. {@code slug}
 * drives the public {@code /c/{slug}} URL.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "name_he", nullable = false)
    private String nameHe;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getNameHe() { return nameHe; }
    public void setNameHe(String nameHe) { this.nameHe = nameHe; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
}
