package com.optiplant.identity.service;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

public record SessionPrincipal(UUID externalId, long credentialsVersion, String role,
                               UUID branchExternalId) implements Principal, Serializable {
    @Override public String getName() { return externalId.toString(); }
}
