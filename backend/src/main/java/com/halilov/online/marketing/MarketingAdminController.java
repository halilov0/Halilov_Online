package com.halilov.online.marketing;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import com.halilov.online.audit.AuditAction;
import com.halilov.online.audit.AuditService;

@RestController
@RequestMapping("/api/admin/marketing")
public class MarketingAdminController {

    private final MarketingService service;
    private final AuditService audit;

    public MarketingAdminController(MarketingService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/recipients")
    public MarketingDtos.RecipientCount recipientCount() {
        return new MarketingDtos.RecipientCount(service.eligibleCount());
    }

    @PostMapping("/broadcast")
    public MarketingDtos.BroadcastResult broadcast(@Valid @RequestBody MarketingDtos.BroadcastRequest req) {
        MarketingDtos.BroadcastResult result = service.broadcast(req);
        audit.record(AuditAction.MARKETING_CAMPAIGN_SENT, "campaign", null,
            "קמפיין נשלח: \"" + req.subject() + "\" → " + result.queued() + " נמענים");
        return result;
    }
}
