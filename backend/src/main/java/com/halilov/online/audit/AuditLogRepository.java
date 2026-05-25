package com.halilov.online.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:userId IS NULL OR a.actorUserId = :userId)
          AND (:action IS NULL OR a.action = :action)
          AND (:from IS NULL OR a.createdAt >= :from)
          AND (:to IS NULL OR a.createdAt < :to)
        ORDER BY a.createdAt DESC, a.id DESC
        """)
    Page<AuditLog> search(@Param("userId") Long userId,
                          @Param("action") String action,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);

    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<String> distinctActions();
}
