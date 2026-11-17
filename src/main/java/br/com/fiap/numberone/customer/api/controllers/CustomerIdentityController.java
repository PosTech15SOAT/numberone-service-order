package br.com.fiap.numberone.customer.api.controllers;

import br.com.fiap.numberone.customer.application.gateways.CustomerGateway;
import br.com.fiap.numberone.customer.domain.enums.TipoDocumento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@RestController
public class CustomerIdentityController {

    private final CustomerGateway customers;
    private final byte[] expectedAuthorization;
    private final boolean enabled;

    public CustomerIdentityController(CustomerGateway customers, @Value("${OS_IDENTITY_TOKEN:}") String token) {
        this.customers = customers;
        this.enabled = token.length() >= 32;
        this.expectedAuthorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/internal/auth/customer-identity")
    public ResponseEntity<Void> verifyCustomerIdentity(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody CustomerIdentityRequest request) {
        if (!enabled || authorization == null || !MessageDigest.isEqual(
                expectedAuthorization, authorization.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).build();
        }

        boolean active = customers.findById(request.customerId())
                .filter(customer -> Boolean.TRUE.equals(customer.getActive()))
                .filter(customer -> customer.getDocumentType() == TipoDocumento.PESSOA_FISICA)
                .filter(customer -> request.cpf().equals(onlyDigits(customer.getDocument())))
                .isPresent();
        return active ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private static String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    public record CustomerIdentityRequest(
            @NotNull UUID customerId,
            @NotBlank @Pattern(regexp = "[0-9]{11}") String cpf) {
    }
}
