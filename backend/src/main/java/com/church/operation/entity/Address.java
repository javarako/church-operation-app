package com.church.operation.entity;

public record Address(
    String addressLine1,
    String addressLine2,
    String city,
    String provinceState,
    String postalZipCode,
    String country
) {
}
