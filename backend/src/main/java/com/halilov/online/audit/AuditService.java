package com.halilov.online.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.halilov.online.common.ClientIp;
import com.halilov.online.user.UserRepository;

/**
 * Append-only audit writer. Every security-sensitive and order-state
 * action goes through one of the {@code record*} methods, which insert
 * one row into {@code audit_log} in a {@code REQUIRES_NEW} transaction.
 *
 * <p>The new transaction is structural — an audit insert failure can
 * never roll back the caller's work, and a caller's rollback can never
 * lose the audit entry. That last property is what lets us audit failed
 * logins from inside the auth path that ultimately throws 401.
 *
 * <p>{@link #record} resolves the actor from
 * {@link org.springframework.security.core.context.SecurityContextHolder}
 * and the IP from the current servlet request. Use
 * {@link #recordAs} when no security context is available yet — e.g.
 * during register / pre-auth login.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repo;
    private final UserRepository users;

    public AuditService(AuditLogRepository repo, UserRepository users) {
        this.repo = repo;
        this.users = users;
    }

    /**
     * Persist one audit row. Resolves the actor from the SecurityContext and
     * the IP from the current HTTP request (if any). Runs in a NEW transaction
     * so an audit insert failure can never roll back the caller's work, and a
     * caller's rollback can never lose the audit trail (e.g. login-failed
     * should survive even though the auth path threw 401).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String targetType, Object targetId, String message, String metadata) {
        try {
            AuditLog row = new AuditLog();
            row.setAction(action);
            row.setTargetType(targetType);
            row.setTargetId(targetId == null ? null : String.valueOf(targetId));
            row.setMessage(message);
            row.setMetadata(metadata);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getName())) {
                String email = auth.getName();
                row.setActorEmail(email);
                auth.getAuthorities().stream().findFirst().ifPresent(a -> {
                    String name = a.getAuthority();
                    if (name.startsWith("ROLE_")) name = name.substring(5);
                    row.setActorRole(name);
                });
                users.findByEmail(email).ifPresent(u -> row.setActorUserId(u.getId()));
            }

            row.setActorIp(currentRequestIp());
            repo.save(row);
        } catch (Exception e) {
            log.warn("audit_log insert failed for action={}, target={}/{}: {}",
                action, targetType, targetId, e.toString());
        }
    }

    public void record(String action, String targetType, Object targetId, String message) {
        record(action, targetType, targetId, message, null);
    }

    /**
     * Record an action whose actor we know explicitly (e.g. a login attempt
     * for an email that may not exist, or a guest checkout where the
     * SecurityContext is empty).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAs(Long actorUserId, String actorEmail, String actorRole,
                         String action, String targetType, Object targetId,
                         String message, String metadata) {
        try {
            AuditLog row = new AuditLog();
            row.setAction(action);
            row.setTargetType(targetType);
            row.setTargetId(targetId == null ? null : String.valueOf(targetId));
            row.setMessage(message);
            row.setMetadata(metadata);
            row.setActorUserId(actorUserId);
            row.setActorEmail(actorEmail);
            row.setActorRole(actorRole);
            row.setActorIp(currentRequestIp());
            repo.save(row);
        } catch (Exception e) {
            log.warn("audit_log insert failed for action={}, target={}/{}: {}",
                action, targetType, targetId, e.toString());
        }
    }

    private static String currentRequestIp() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            return ClientIp.resolve(attrs.getRequest());
        } catch (Exception e) {
            return null;
        }
    }
}
