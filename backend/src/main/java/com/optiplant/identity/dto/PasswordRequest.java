package com.optiplant.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Password change of the session user, bounded by the 15 to 128 character password policy. */
public record PasswordRequest(@NotNull @Size(min = 15, max = 128) String currentPassword,
                              @NotNull @Size(min = 15, max = 128) String newPassword) {}
