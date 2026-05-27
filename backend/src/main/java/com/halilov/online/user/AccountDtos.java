package com.halilov.online.user;

import jakarta.validation.constraints.*;

/**
 * Request and response records for {@link AccountController} — profile
 * updates, marketing-consent toggle, saved-address upsert and view.
 */
public class AccountDtos {

    public record ProfileUpdate(
        @NotBlank @Size(max = 255) String fullName,
        @Size(max = 32) String phone
    ) {}

    public record MarketingConsentUpdate(boolean optIn) {}

    public record AddressView(
        Long id,
        String label,
        String fullName,
        String phone,
        String street,
        String houseNo,
        String apartment,
        String city,
        String postalCode,
        String notes,
        boolean isDefault
    ) {
        public static AddressView from(SavedAddress a) {
            return new AddressView(
                a.getId(), a.getLabel(), a.getFullName(), a.getPhone(),
                a.getStreet(), a.getHouseNo(), a.getApartment(), a.getCity(),
                a.getPostalCode(), a.getNotes(), a.isDefault()
            );
        }
    }

    public record AddressUpsert(
        @Size(max = 64) String label,
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Size(max = 32)  String phone,
        @NotBlank @Size(max = 255) String street,
        @Size(max = 32) String houseNo,
        @Size(max = 32) String apartment,
        @NotBlank @Size(max = 128) String city,
        @Size(max = 16) String postalCode,
        @Size(max = 500) String notes,
        boolean isDefault
    ) {}
}
