package com.optiplant.identity.service;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/**
 * Snapshot of the account stored in the HTTP session, which is why it is serializable. Each request compares it with the
 * current account, so a change of credentials, role or branch invalidates the session.
 */
public record SessionPrincipal(UUID externalId, long credentialsVersion, String role,
                               UUID branchExternalId) implements Principal, Serializable {
    @Override public String getName() { return externalId.toString(); }
}
