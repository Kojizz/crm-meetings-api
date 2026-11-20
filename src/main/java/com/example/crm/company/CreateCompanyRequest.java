package com.example.crm.company;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(
        @JsonProperty("name")
        @NotBlank
        @Size(max = 255)
        String name,

        @JsonProperty("bin")
        @NotNull
        @Pattern(regexp = "\\d{12}", message = "must be exactly 12 digits")
        String bin,

        @JsonProperty("phone")
        @NotNull
        @Pattern(regexp = "\\+?[0-9 ()\\-]{7,20}", message = "must be a phone number like +7 701 000 00 00")
        String phone
) {
}
