package com.halilov.online.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PutMapping("/profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateProfile(Authentication auth, @Valid @RequestBody AccountDtos.ProfileUpdate req) {
        accountService.updateProfile(requireEmail(auth), req);
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
