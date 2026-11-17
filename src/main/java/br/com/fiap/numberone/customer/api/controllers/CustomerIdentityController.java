package br.com.fiap.numberone.customer.api.controllers;

import br.com.fiap.numberone.customer.application.services.CustomerService;
import br.com.fiap.numberone.customer.domain.entities.Customer;
import br.com.fiap.numberone.customer.domain.enums.TipoDocumento;
import br.com.fiap.numberone.customer.domain.exceptions.CustomerNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@RestController
@RequestMapping("/internal/auth")
public class CustomerIdentityController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final CustomerService customerService;
    private final String identityToken;

    public CustomerIdentityController(CustomerService customerService,
                                      @Value("${OS_IDENTITY_TOKEN:}") String identityToken) {
        this.customerService = customerService;
        this.identityToken = identityToken;
    }

    @PostMapping("/customer-identity")
    public ResponseEntity<Void> verifyCustomerIdentity(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CustomerIdentityRequest request) {
        if (!hasValidToken(authorization)) {
            return ResponseEntity.status(401).build();
        }

        try {
            Customer customer = customerService.findById(request.customerId());
            if (Boolean.TRUE.equals(customer.getActive())
                    && customer.getDocumentType() == TipoDocumento.PESSOA_FISICA
                    && onlyDigits(customer.getDocument()).equals(request.cpf())) {
                return ResponseEntity.noContent().build();
            }
        } catch (CustomerNotFoundException ignored) {
            // Keep absent, inactive, and mismatched identities indistinguishable to the caller.
        }
        return ResponseEntity.notFound().build();
    }

    private boolean hasValidToken(String authorization) {
        if (identityToken.isBlank() || authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return false;
        }
        String providedToken = authorization.substring(BEARER_PREFIX.length());
        return !providedToken.isBlank() && MessageDigest.isEqual(
                identityToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8));
    }

    private String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    public record CustomerIdentityRequest(
            @NotNull UUID customerId,
            @NotBlank @Pattern(regexp = "[0-9]{11}") String cpf) {
    }
}
