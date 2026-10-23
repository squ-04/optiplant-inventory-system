package com.optiplant.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PasswordRequest(@NotNull @Size(min = 15, max = 128) String currentPassword,
                              @NotNull @Size(min = 15, max = 128) String newPassword) {}
