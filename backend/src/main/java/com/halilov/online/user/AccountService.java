package com.halilov.online.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.audit.AuditAction;
import com.halilov.online.audit.AuditService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Logged-in user self-service: profile edits, password change, saved
 * addresses, marketing opt-in toggle. The authenticated email comes
 * from the JWT (passed in by {@link AccountController}); the service
 * never reads identity from request bodies.
 *
 * <p>Saved addresses live in their own table and are never mutated
 * by order writes — orders snapshot their shipping address at
 * checkout so a future profile edit doesn't rewrite shipped invoices.
 */
@Service
public class AccountService {

    private final UserRepository users;
    private final SavedAddressRepository addresses;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository users, SavedAddressRepository addresses,
                          AuditService audit, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.addresses = addresses;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void updateProfile(String email, AccountDtos.ProfileUpdate req) {
        User u = users.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no session"));
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        audit.record(AuditAction.ACCOUNT_PROFILE_UPDATED, "user", u.getId(),
            "פרופיל עודכן: " + u.getEmail());
    }

    /**
     * Self-service password change for the logged-in user. Verifies the
     * current password (so the JWT alone isn't enough), rejects unchanged
     * passwords, rotates the hash, and stamps {@code force_logout_at} so
     * every JWT issued before this call — including from other devices —
     * is invalidated. The caller (controller) is responsible for issuing a
     * fresh JWT for the current request and returning it to the client.
     */
    @Transactional
    public User changePassword(String email, AccountDtos.PasswordChange req) {
        User u = requireUser(email);
        if (!passwordEncoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "הסיסמה הנוכחית שגויה");
        }
        if (passwordEncoder.matches(req.newPassword(), u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "אנא בחרו סיסמה שונה מהקודמת");
        }
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        // Truncated to seconds so the freshly-issued JWT (iat at whole-second
        // precision) is >= force_logout_at and survives the auth-filter check.
        u.setForceLogoutAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        audit.recordAs(u.getId(), u.getEmail(), u.getRole().name(),
            AuditAction.ACCOUNT_PASSWORD_CHANGED, "user", u.getId(),
            "סיסמה הוחלפה: " + u.getEmail(), null);
        return u;
    }

    @Transactional
    public void updateMarketingConsent(String email, boolean optIn) {
        User u = requireUser(email);
        if (optIn == u.isMarketingOptIn()) return;
        u.setMarketingOptIn(optIn);
        if (optIn) {
            u.setMarketingConsentAt(Instant.now());
            if (u.getUnsubscribeToken() == null || u.getUnsubscribeToken().isBlank()) {
                u.setUnsubscribeToken(UUID.randomUUID().toString().replace("-", ""));
            }
        }
        audit.record(AuditAction.ACCOUNT_MARKETING_CHANGED, "user", u.getId(),
            "הסכמה לשיווק: " + (optIn ? "אופטין" : "אופטאאוט"));
    }

    @Transactional(readOnly = true)
    public List<AccountDtos.AddressView> listAddresses(String email) {
        User u = requireUser(email);
        return addresses.findByUserIdOrderByIsDefaultDescCreatedAtDesc(u.getId()).stream()
            .map(AccountDtos.AddressView::from)
            .toList();
    }

    @Transactional
    public AccountDtos.AddressView createAddress(String email, AccountDtos.AddressUpsert req) {
        User u = requireUser(email);
        SavedAddress a = new SavedAddress();
        a.setUserId(u.getId());
        apply(a, req);
        // first address auto-becomes default
        if (addresses.countByUserId(u.getId()) == 0) {
            a.setDefault(true);
        } else if (req.isDefault()) {
            clearDefault(u.getId());
        }
        SavedAddress saved = addresses.save(a);
        audit.record(AuditAction.ACCOUNT_ADDRESS_SAVED, "address", saved.getId(),
            "כתובת נשמרה: " + saved.getCity() + ", " + saved.getStreet());
        return AccountDtos.AddressView.from(saved);
    }

    @Transactional
    public AccountDtos.AddressView updateAddress(String email, Long id, AccountDtos.AddressUpsert req) {
        User u = requireUser(email);
        SavedAddress a = addresses.findByIdAndUserId(id, u.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "address not found"));
        apply(a, req);
        if (req.isDefault() && !a.isDefault()) {
            clearDefault(u.getId());
            a.setDefault(true);
        }
        return AccountDtos.AddressView.from(a);
    }

    @Transactional
    public void deleteAddress(String email, Long id) {
        User u = requireUser(email);
        SavedAddress a = addresses.findByIdAndUserId(id, u.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "address not found"));
        boolean wasDefault = a.isDefault();
        addresses.delete(a);
        if (wasDefault) {
            // promote oldest remaining to default for convenience
            addresses.findByUserIdOrderByIsDefaultDescCreatedAtDesc(u.getId()).stream()
                .findFirst().ifPresent(next -> next.setDefault(true));
        }
        audit.record(AuditAction.ACCOUNT_ADDRESS_DELETED, "address", id, "כתובת נמחקה (#" + id + ")");
    }

    @Transactional
    public AccountDtos.AddressView setDefault(String email, Long id) {
        User u = requireUser(email);
        SavedAddress a = addresses.findByIdAndUserId(id, u.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "address not found"));
        if (!a.isDefault()) {
            clearDefault(u.getId());
            a.setDefault(true);
        }
        return AccountDtos.AddressView.from(a);
    }

    private void clearDefault(Long userId) {
        addresses.findFirstByUserIdAndIsDefaultTrue(userId).ifPresent(cur -> {
            cur.setDefault(false);
            addresses.saveAndFlush(cur);
        });
    }

    private User requireUser(String email) {
        return users.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no session"));
    }

    private void apply(SavedAddress a, AccountDtos.AddressUpsert req) {
        a.setLabel(blankToNull(req.label()));
        a.setFullName(req.fullName().trim());
        a.setPhone(req.phone().trim());
        a.setStreet(req.street().trim());
        a.setHouseNo(blankToNull(req.houseNo()));
        a.setApartment(blankToNull(req.apartment()));
        a.setCity(req.city().trim());
        a.setPostalCode(blankToNull(req.postalCode()));
        a.setNotes(blankToNull(req.notes()));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
