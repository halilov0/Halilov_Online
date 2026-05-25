package com.halilov.online.audit;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    default Page<AuditLog> search(Long userId, String action, Instant from, Instant to, Pageable pageable) {
        // Hand-built Specification because Postgres can't infer the type of a
        // null-bound parameter inside `:p IS NULL OR col = :p`. Building the
        // predicates only when a filter is actually present sidesteps that
        // entirely and keeps the generated SQL clean.
        Specification<AuditLog> spec = (root, q, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (userId != null) preds.add(cb.equal(root.get("actorUserId"), userId));
            if (action != null && !action.isBlank()) preds.add(cb.equal(root.get("action"), action));
            if (from != null) preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) preds.add(cb.lessThan(root.get("createdAt"), to));
            return cb.and(preds.toArray(new Predicate[0]));
        };
        Pageable sorted = pageable.getSort().isUnsorted()
            ? org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"))
            : pageable;
        return findAll(spec, sorted);
    }

    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<String> distinctActions();
}
