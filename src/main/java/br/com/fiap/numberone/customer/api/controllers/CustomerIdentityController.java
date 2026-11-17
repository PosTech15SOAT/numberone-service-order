package br.com.fiap.numberone.customer.api.controllers;

import br.com.fiap.numberone.customer.application.gateways.CustomerGateway;
import br.com.fiap.numberone.customer.domain.enums.TipoDocumento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class CustomerIdentityController {

    private final CustomerGateway customers;
    public CustomerIdentityController(CustomerGateway customers) {
        this.customers = customers;
    }

    @PostMapping("/internal/auth/customer-identity")
    public ResponseEntity<Void> verifyCustomerIdentity(
            @Valid @RequestBody CustomerIdentityRequest request) {
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
