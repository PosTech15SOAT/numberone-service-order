package br.com.fiap.numberone.customer.api.controllers;

import br.com.fiap.numberone.customer.application.gateways.CustomerGateway;
import br.com.fiap.numberone.customer.domain.enums.TipoDocumento;
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
    private final byte[] authorization;
    private final boolean enabled;

    public CustomerIdentityController(CustomerGateway customers, @Value("${OS_IDENTITY_TOKEN:}") String token) {
        this.customers = customers;
        this.enabled = token.length() >= 32;
        this.authorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/internal/auth/customer-identity")
    public ResponseEntity<Void> verify(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                       String bearer, @RequestBody Identity identity) {
        if (!enabled || bearer == null
                || !MessageDigest.isEqual(authorization, bearer.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).build();
        }
        if (identity.customerId() == null || identity.cpf() == null || !identity.cpf().matches("[0-9]{11}")) {
            return ResponseEntity.badRequest().build();
        }
        boolean active = customers.findById(identity.customerId())
                .filter(customer -> Boolean.TRUE.equals(customer.getActive()))
                .filter(customer -> customer.getDocumentType() == TipoDocumento.PESSOA_FISICA)
                .filter(customer -> identity.cpf().equals(customer.getDocument().replaceAll("\\D", "")))
                .isPresent();
        return active ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    public record Identity(UUID customerId, String cpf) { }
}
