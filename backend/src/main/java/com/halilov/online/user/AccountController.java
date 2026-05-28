package com.halilov.online.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.auth.AuthDtos;
import com.halilov.online.security.JwtService;

import java.util.List;

/**
 * Logged-in user self-service under {@code /api/me}. Profile edits,
 * password change, saved-address CRUD, and the marketing opt-in
 * toggle. All routes require a valid bearer token; the email is taken
 * from the JWT, never the request body.
 */
@RestController
@RequestMapping("/api/me")
public class AccountController {

    private final AccountService accountService;
    private final JwtService jwtService;

    public AccountController(AccountService accountService, JwtService jwtService) {
        this.accountService = accountService;
        this.jwtService = jwtService;
    }

    @PutMapping("/profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateProfile(Authentication auth, @Valid @RequestBody AccountDtos.ProfileUpdate req) {
        accountService.updateProfile(requireEmail(auth), req);
    }

    /**
     * Self-service password change. Returns a freshly issued JWT — the
     * service stamps {@code force_logout_at = now} so the caller's old
     * token would fail on the next request; the new one's {@code iat}
     * sits at or after that stamp and survives the filter check.
     */
    @PatchMapping("/password")
    public AuthDtos.TokenResponse changePassword(Authentication auth,
                                                 @Valid @RequestBody AccountDtos.PasswordChange req) {
        User u = accountService.changePassword(requireEmail(auth), req);
        String token = jwtService.issue(u);
        return new AuthDtos.TokenResponse(token, u.getEmail(), u.getRole().name(), u.getFullName());
    }

    @PutMapping("/marketing-consent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateMarketingConsent(Authentication auth, @RequestBody AccountDtos.MarketingConsentUpdate req) {
        accountService.updateMarketingConsent(requireEmail(auth), req.optIn());
    }

    @GetMapping("/addresses")
    public List<AccountDtos.AddressView> listAddresses(Authentication auth) {
        return accountService.listAddresses(requireEmail(auth));
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDtos.AddressView createAddress(Authentication auth, @Valid @RequestBody AccountDtos.AddressUpsert req) {
        return accountService.createAddress(requireEmail(auth), req);
    }

    @PutMapping("/addresses/{id}")
    public AccountDtos.AddressView updateAddress(Authentication auth, @PathVariable Long id,
                                                 @Valid @RequestBody AccountDtos.AddressUpsert req) {
        return accountService.updateAddress(requireEmail(auth), id, req);
    }

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(Authentication auth, @PathVariable Long id) {
        accountService.deleteAddress(requireEmail(auth), id);
    }

    @PostMapping("/addresses/{id}/default")
    public AccountDtos.AddressView setDefaultAddress(Authentication auth, @PathVariable Long id) {
        return accountService.setDefault(requireEmail(auth), id);
    }

    private static String requireEmail(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no session");
        }
        return auth.getName();
    }
}
