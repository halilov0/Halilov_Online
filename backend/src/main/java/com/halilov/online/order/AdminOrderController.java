package com.halilov.online.order;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin order management under {@code /api/admin/orders}. Lists every
 * order, drives the status machine (including the
 * {@code PENDING → PAID} stock decrement), issues refunds, and exports
 * the orders table as a UTF-8 CSV with BOM (so Excel renders Hebrew
 * correctly out of the box).
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderDtos.OrderView> all() {
        return orderService.adminListAll();
    }

    @GetMapping("/{orderNumber}")
    public OrderDtos.OrderView one(@PathVariable String orderNumber) {
        return orderService.adminGet(orderNumber);
    }

    @PatchMapping("/{orderNumber}/status")
    public OrderDtos.OrderView updateStatus(
        @PathVariable String orderNumber,
        @Valid @RequestBody OrderDtos.UpdateStatusRequest req
    ) {
        return orderService.adminUpdateStatus(orderNumber, req.status());
    }

    @PostMapping("/{orderNumber}/refund")
    public OrderDtos.OrderView refund(
        @PathVariable String orderNumber,
        @Valid @RequestBody OrderDtos.RefundRequest req
    ) {
        return orderService.adminRefund(orderNumber, req.amountAgorot(), req.reason(), req.restoreStock());
    }

    @GetMapping(value = "/export.csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<String> exportOrdersCsv() {
        String csv = orderService.exportOrdersCsv();
        String filename = "orders-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }
}
