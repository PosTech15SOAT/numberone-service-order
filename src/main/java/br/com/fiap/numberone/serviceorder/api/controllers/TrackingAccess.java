package br.com.fiap.numberone.serviceorder.api.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

final class TrackingAccess {
    private TrackingAccess() { }

    // These headers are overwritten by the private API Gateway integration.
    // A null customer scope represents an authorized operator.
    static UUID customerScope(HttpServletRequest request) {
        if (!"ACTIVE".equals(request.getHeader("X-Authenticated-Status"))
                || canonicalUuid(request.getHeader("X-Authenticated-Subject")) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }

        String role = request.getHeader("X-Authenticated-Roles");
        String permissions = request.getHeader("X-Authenticated-Permissions");
        if ("ADMIN".equals(role) && includes(permissions, "ADMIN_ACCESS")) {
            return null;
        }
        UUID customer = canonicalUuid(request.getHeader("X-Authenticated-Customer-Id"));
        if ("CUSTOMER".equals(role) && customer != null && includes(permissions, "SERVICE_ORDER_TRACK_OWN")) {
            return customer;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    private static UUID canonicalUuid(String value) {
        if (value == null || value.length() != 36) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(value);
            return uuid.toString().equals(value) ? uuid : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean includes(String permissions, String required) {
        return permissions != null && java.util.Arrays.asList(permissions.split(",")).contains(required);
    }
}
