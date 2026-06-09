package com.halilov.online.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request and response records for {@link AuthController}. Bean
 * Validation annotations enforce input shape at the HTTP boundary;
 * the service layer trusts what arrives.
 */
public class AuthDtos {

    public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 255) String fullName,
        @Size(max = 32) String phone,
        Boolean marketingOptIn
    ) {}

    public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
    ) {}

    public record TokenResponse(String token, String email, String role, String fullName) {}

    public record MeResponse(Long id, String email, String fullName, String phone, String role, boolean marketingOptIn) {}

    /** Response from /api/auth/login when the account has 2FA enabled and
     *  the client IP isn't on the trusted list. Client must POST the
     *  challenge token + a TOTP/recovery code to /api/auth/login/totp to
     *  receive an actual JWT. */
    public record ChallengeResponse(boolean requires2FA, String challenge) {}

    public record TotpLoginRequest(
        @NotBlank String challenge,
        @NotBlank String code,
        /** When true, issue a 30-day cookie so this browser skips 2FA next time. */
        Boolean trustDevice
    ) {}
}
