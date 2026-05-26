package com.halilov.online.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService resetService;

    public AuthController(AuthService authService, PasswordResetService resetService) {
        this.authService = authService;
        this.resetService = resetService;
    }

    @PostMapping("/register")
    public AuthDtos.TokenResponse register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        return authService.login(req);
    }

    @GetMapping("/me")
    public AuthDtos.MeResponse me(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no session");
        }
        return authService.me(auth.getName());
    }

    /**
     * Forgot-password from the login page. Always returns 204, regardless of
     * whether the email exists, to prevent enumeration.
     */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        resetService.requestForEmail(req.email());
    }

    @GetMapping("/password-reset/{token}")
    public PasswordResetService.TokenInfo validateReset(@PathVariable String token) {
        return resetService.validate(token);
    }

    @PostMapping("/password-reset/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeReset(@PathVariable String token,
                              @Valid @RequestBody CompleteResetRequest req) {
        resetService.complete(token, req.newPassword());
    }

    public record ForgotPasswordRequest(@Email @NotBlank String email) {}
    public record CompleteResetRequest(@NotBlank @Size(min = 8, max = 100) String newPassword) {}
}
