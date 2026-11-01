package br.com.fiap.numberone.customer.domain.entities;

import br.com.fiap.numberone.customer.domain.enums.TipoDocumento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    private UUID id;
    private Long version;
    private String name;
    private TipoDocumento documentType;
    private String document;
    private String email;
    private String phone;
    private String address;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Customer updateFrom(Customer newCustomer) {
        return Customer.builder()
                .id(this.id)
                .version(this.version)
                .name(newCustomer.name)
                .documentType(newCustomer.documentType)
                .document(newCustomer.document)
                .email(newCustomer.email)
                .phone(newCustomer.phone)
                .address(newCustomer.address)
                .active(newCustomer.active)
                .createdAt(this.createdAt)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
