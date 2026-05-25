package com.halilov.online.coupon;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.halilov.online.audit.AuditAction;
import com.halilov.online.audit.AuditService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/coupons")
public class CouponAdminController {

    private final CouponService coupons;
    private final AuditService audit;

    public CouponAdminController(CouponService coupons, AuditService audit) {
        this.coupons = coupons;
        this.audit = audit;
    }

    @GetMapping
    public List<CouponDtos.CouponView> list() {
        return coupons.adminListAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponDtos.CouponView create(@Valid @RequestBody CouponDtos.CouponUpsert req) {
        CouponDtos.CouponView c = coupons.adminCreate(req);
        audit.record(AuditAction.COUPON_CREATED, "coupon", c.id(), "קופון נוצר: " + c.code());
        return c;
    }

    @PutMapping("/{id}")
    public CouponDtos.CouponView update(@PathVariable Long id, @Valid @RequestBody CouponDtos.CouponUpsert req) {
        CouponDtos.CouponView c = coupons.adminUpdate(id, req);
        audit.record(AuditAction.COUPON_UPDATED, "coupon", id, "קופון עודכן: " + c.code());
        return c;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        coupons.adminDelete(id);
        audit.record(AuditAction.COUPON_DELETED, "coupon", id, "קופון נמחק (#" + id + ")");
    }
}
