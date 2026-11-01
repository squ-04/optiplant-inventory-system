package com.optiplant.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Login credentials, bounded by the same size limits as the account code and password policy. */
public record LoginRequest(@NotBlank @Size(max = 64) String code,
                           @NotNull @Size(min = 15, max = 128) String password) {}
