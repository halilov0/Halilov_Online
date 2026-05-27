package com.halilov.online.metrics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.halilov.online.catalog.Product;
import com.halilov.online.catalog.ProductRepository;
import com.halilov.online.order.Address;
import com.halilov.online.order.AddressRepository;
import com.halilov.online.order.Order;
import com.halilov.online.order.OrderItem;
import com.halilov.online.order.OrderRepository;
import com.halilov.online.order.OrderStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates the small set of counters surfaced on the admin
 * dashboard: today's orders, today's revenue, low-stock products,
 * recent signups, top sellers. All times are computed in
 * {@code Asia/Jerusalem} so "today" matches the operator's calendar.
 *
 * <p>Intentionally narrow — anything that wants real time-series
 * granularity belongs in a metrics pipeline, not here.
 */
import java.util.TreeMap;

/**
 * Aggregates dashboard metrics. Computation is in-memory over the full order set —
 * acceptable for an MVP shop (< 10k orders). Swap to native SQL aggregations when
 * volume warrants it; the controller contract won't change.
 */
@Service
public class MetricsService {

    /** Statuses that contribute to recognized revenue. PENDING/CANCELLED/REFUNDED do not. */
    private static final Set<OrderStatus> REVENUE_STATUSES = EnumSet.of(
        OrderStatus.PAID, OrderStatus.FULFILLED, OrderStatus.SHIPPED, OrderStatus.DELIVERED
    );
    private static final ZoneId IL_ZONE = ZoneId.of("Asia/Jerusalem");
    private static final int BARS_DAYS = 14;
    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final int LIMIT_TOP_PRODUCTS = 5;
    private static final int LIMIT_LOW_STOCK = 10;
    private static final int LIMIT_RECENT_ORDERS = 5;

    private final OrderRepository orders;
    private final ProductRepository products;
    private final AddressRepository addresses;

    public MetricsService(OrderRepository orders, ProductRepository products, AddressRepository addresses) {
        this.orders = orders;
        this.products = products;
        this.addresses = addresses;
    }

    @Transactional(readOnly = true)
    public MetricsDtos.DashboardMetrics getDashboard() {
        List<Order> allOrders = orders.findAllByOrderByCreatedAtDesc();
        List<Product> allProducts = products.findAll();

        return new MetricsDtos.DashboardMetrics(
            kpi(allOrders),
            statusCounts(allOrders),
            dailyRevenue(allOrders),
            topProducts(allOrders, allProducts),
            lowStock(allProducts),
            recentOrders(allOrders)
        );
    }

    private MetricsDtos.Kpi kpi(List<Order> all) {
        LocalDate today = LocalDate.now(IL_ZONE);
        Instant startToday  = today.atStartOfDay(IL_ZONE).toInstant();
        Instant start7      = today.minusDays(6).atStartOfDay(IL_ZONE).toInstant();
        Instant start30     = today.minusDays(29).atStartOfDay(IL_ZONE).toInstant();

        long revToday = 0, rev7 = 0, rev30 = 0, revLife = 0;
        long ordersToday = 0;
        long paidLife = 0;
        for (Order o : all) {
            boolean revenue = REVENUE_STATUSES.contains(o.getStatus());
            Instant t = o.getCreatedAt();
            if (!t.isBefore(startToday)) ordersToday++;
            if (revenue) {
                paidLife++;
                revLife += o.getTotalAgorot();
                if (!t.isBefore(start30)) rev30 += o.getTotalAgorot();
                if (!t.isBefore(start7))  rev7  += o.getTotalAgorot();
                if (!t.isBefore(startToday)) revToday += o.getTotalAgorot();
            }
        }
        long aov = paidLife > 0 ? revLife / paidLife : 0;
        return new MetricsDtos.Kpi(revToday, rev7, rev30, revLife,
            ordersToday, all.size(), paidLife, aov);
    }

    private Map<String, Long> statusCounts(List<Order> all) {
        // Stable order matching OrderStatus enum declaration so the UI can render in pipeline order.
        Map<String, Long> out = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) out.put(s.name(), 0L);
        for (Order o : all) out.merge(o.getStatus().name(), 1L, Long::sum);
        return out;
    }

    private List<MetricsDtos.DailyBucket> dailyRevenue(List<Order> all) {
        LocalDate today = LocalDate.now(IL_ZONE);
        TreeMap<LocalDate, long[]> buckets = new TreeMap<>(); // [revenue, count]
        for (int i = BARS_DAYS - 1; i >= 0; i--) {
            buckets.put(today.minusDays(i), new long[] { 0, 0 });
        }
        for (Order o : all) {
            if (!REVENUE_STATUSES.contains(o.getStatus())) continue;
            LocalDate d = o.getCreatedAt().atZone(IL_ZONE).toLocalDate();
            long[] cell = buckets.get(d);
            if (cell != null) {
                cell[0] += o.getTotalAgorot();
                cell[1] += 1;
            }
        }
        List<MetricsDtos.DailyBucket> out = new ArrayList<>(buckets.size());
        buckets.forEach((d, cell) -> out.add(new MetricsDtos.DailyBucket(d.toString(), cell[0], cell[1])));
        return out;
    }

    private List<MetricsDtos.TopProduct> topProducts(List<Order> all, List<Product> allProducts) {
        Map<Long, Product> productById = new HashMap<>();
        for (Product p : allProducts) productById.put(p.getId(), p);

        Map<Long, long[]> agg = new HashMap<>(); // productId -> [qty, revenue]
        for (Order o : all) {
            if (!REVENUE_STATUSES.contains(o.getStatus())) continue;
            for (OrderItem it : o.getItems()) {
                long[] cell = agg.computeIfAbsent(it.getProductId(), k -> new long[2]);
                cell[0] += it.getQuantity();
                cell[1] += it.getLineTotalAgorot();
            }
        }
        return agg.entrySet().stream()
            .sorted(Comparator.<Map.Entry<Long, long[]>>comparingLong(e -> e.getValue()[0]).reversed())
            .limit(LIMIT_TOP_PRODUCTS)
            .map(e -> {
                Product p = productById.get(e.getKey());
                String name = p != null ? p.getNameHe() : ("#" + e.getKey());
                String sku  = p != null ? p.getSku()    : "";
                return new MetricsDtos.TopProduct(e.getKey(), name, sku, e.getValue()[0], e.getValue()[1]);
            })
            .toList();
    }

    private List<MetricsDtos.LowStockItem> lowStock(List<Product> all) {
        return all.stream()
            .filter(Product::isActive)
            .filter(p -> p.getStockQty() < LOW_STOCK_THRESHOLD)
            .sorted(Comparator.comparingInt(Product::getStockQty))
            .limit(LIMIT_LOW_STOCK)
            .map(p -> new MetricsDtos.LowStockItem(p.getId(), p.getNameHe(), p.getSku(), p.getStockQty()))
            .toList();
    }

    private List<MetricsDtos.RecentOrder> recentOrders(List<Order> all) {
        // `all` already sorted desc by createdAt — take head.
        List<Order> recent = all.size() > LIMIT_RECENT_ORDERS ? all.subList(0, LIMIT_RECENT_ORDERS) : all;
        List<MetricsDtos.RecentOrder> out = new ArrayList<>(recent.size());
        for (Order o : recent) {
            Address addr = o.getShippingAddressId() != null
                ? addresses.findById(o.getShippingAddressId()).orElse(null) : null;
            long itemCount = o.getItems().stream().mapToLong(OrderItem::getQuantity).sum();
            out.add(new MetricsDtos.RecentOrder(
                o.getOrderNumber(),
                o.getStatus().name(),
                o.getTotalAgorot(),
                o.getCreatedAt(),
                addr != null ? addr.getFullName() : null,
                addr != null ? addr.getCity()     : null,
                itemCount
            ));
        }
        return out;
    }
}
