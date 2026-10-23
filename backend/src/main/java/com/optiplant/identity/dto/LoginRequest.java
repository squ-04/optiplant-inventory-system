package com.optiplant.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 64) String code,
                           @NotNull @Size(min = 15, max = 128) String password) {}
