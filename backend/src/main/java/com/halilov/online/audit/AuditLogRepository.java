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

    /** Native projection used by the anomaly monitor. */
    interface ScopeCount {
        String getScope();
        Long getCnt();
    }

    @Query(value = """
        SELECT actor_email AS scope, COUNT(*) AS cnt
        FROM audit_log
        WHERE action = :action AND created_at >= :since AND actor_email IS NOT NULL
        GROUP BY actor_email
        HAVING COUNT(*) >= :threshold
        """, nativeQuery = true)
    List<ScopeCount> groupByActorEmailSince(@org.springframework.data.repository.query.Param("action") String action,
                                            @org.springframework.data.repository.query.Param("since") Instant since,
                                            @org.springframework.data.repository.query.Param("threshold") long threshold);

    @Query(value = """
        SELECT actor_ip AS scope, COUNT(*) AS cnt
        FROM audit_log
        WHERE action = :action AND created_at >= :since AND actor_ip IS NOT NULL
        GROUP BY actor_ip
        HAVING COUNT(*) >= :threshold
        """, nativeQuery = true)
    List<ScopeCount> groupByActorIpSince(@org.springframework.data.repository.query.Param("action") String action,
                                         @org.springframework.data.repository.query.Param("since") Instant since,
                                         @org.springframework.data.repository.query.Param("threshold") long threshold);

    @Query(value = "SELECT COUNT(*) FROM audit_log WHERE action = :action AND created_at >= :since",
           nativeQuery = true)
    long countActionSince(@org.springframework.data.repository.query.Param("action") String action,
                          @org.springframework.data.repository.query.Param("since") Instant since);

    @Query(value = """
        SELECT actor_email AS scope, COUNT(*) AS cnt
        FROM audit_log
        WHERE action = :action AND actor_ip = :ip
          AND created_at >= :since AND actor_email IS NOT NULL
        GROUP BY actor_email
        ORDER BY COUNT(*) DESC
        LIMIT :maxRows
        """, nativeQuery = true)
    List<ScopeCount> topEmailsForIp(@org.springframework.data.repository.query.Param("action") String action,
                                    @org.springframework.data.repository.query.Param("ip") String ip,
                                    @org.springframework.data.repository.query.Param("since") Instant since,
                                    @org.springframework.data.repository.query.Param("maxRows") int maxRows);

    @Query(value = """
        SELECT actor_ip AS scope, COUNT(*) AS cnt
        FROM audit_log
        WHERE action = :action AND actor_email = :email
          AND created_at >= :since AND actor_ip IS NOT NULL
        GROUP BY actor_ip
        ORDER BY COUNT(*) DESC
        LIMIT :maxRows
        """, nativeQuery = true)
    List<ScopeCount> topIpsForEmail(@org.springframework.data.repository.query.Param("action") String action,
                                    @org.springframework.data.repository.query.Param("email") String email,
                                    @org.springframework.data.repository.query.Param("since") Instant since,
                                    @org.springframework.data.repository.query.Param("maxRows") int maxRows);

    @Query(value = """
        SELECT actor_email AS scope, COUNT(*) AS cnt
        FROM audit_log
        WHERE action = :action AND created_at >= :since AND actor_email IS NOT NULL
        GROUP BY actor_email
        ORDER BY COUNT(*) DESC
        LIMIT :maxRows
        """, nativeQuery = true)
    List<ScopeCount> topActorsForAction(@org.springframework.data.repository.query.Param("action") String action,
                                        @org.springframework.data.repository.query.Param("since") Instant since,
                                        @org.springframework.data.repository.query.Param("maxRows") int maxRows);

    @Query(value = """
        SELECT COUNT(*) FROM audit_log
        WHERE action = :action AND actor_email = :email AND created_at >= :since
        """, nativeQuery = true)
    long countActionForEmailSince(@org.springframework.data.repository.query.Param("action") String action,
                                  @org.springframework.data.repository.query.Param("email") String email,
                                  @org.springframework.data.repository.query.Param("since") Instant since);

    @Query(value = """
        SELECT COUNT(*) FROM audit_log
        WHERE action = :action AND actor_ip = :ip AND created_at >= :since
        """, nativeQuery = true)
    long countActionForIpSince(@org.springframework.data.repository.query.Param("action") String action,
                               @org.springframework.data.repository.query.Param("ip") String ip,
                               @org.springframework.data.repository.query.Param("since") Instant since);
}
