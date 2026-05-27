package com.halilov.online.order;

import jakarta.persistence.*;

/**
 * One line on an {@link Order}. Snapshots the product name, SKU and
 * unit price at order time so future catalog edits never rewrite a
 * shipped invoice. {@code line_total_agorot} = unit price × quantity,
 * stored directly to keep CSV exports and refund math simple.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "name_he", nullable = false)
    private String nameHe;

    @Column(nullable = false)
    private String sku;

    @Column(name = "unit_price_agorot", nullable = false)
    private int unitPriceAgorot;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total_agorot", nullable = false)
    private int lineTotalAgorot;

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getNameHe() { return nameHe; }
    public void setNameHe(String nameHe) { this.nameHe = nameHe; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public int getUnitPriceAgorot() { return unitPriceAgorot; }
    public void setUnitPriceAgorot(int unitPriceAgorot) { this.unitPriceAgorot = unitPriceAgorot; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getLineTotalAgorot() { return lineTotalAgorot; }
    public void setLineTotalAgorot(int lineTotalAgorot) { this.lineTotalAgorot = lineTotalAgorot; }
}
