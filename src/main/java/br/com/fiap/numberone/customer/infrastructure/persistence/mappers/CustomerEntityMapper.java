package br.com.fiap.numberone.customer.infrastructure.persistence.mappers;

import br.com.fiap.numberone.customer.domain.entities.Customer;
import br.com.fiap.numberone.customer.infrastructure.persistence.entities.CustomerEntity;
import org.springframework.stereotype.Component;

@Component
public class CustomerEntityMapper {

    public CustomerEntity toEntity(Customer domain) {
        return CustomerEntity.builder()
                .id(domain.getId())
                .version(domain.getVersion())
                .name(domain.getName())
                .documentType(domain.getDocumentType())
                .document(domain.getDocument())
                .phone(domain.getPhone())
                .address(domain.getAddress())
                .active(domain.getActive())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .email(domain.getEmail())
                .build();
    }

    public Customer toDomain(CustomerEntity entity) {
        return Customer.builder()
                .id(entity.getId())
                .version(entity.getVersion())
                .name(entity.getName())
                .documentType(entity.getDocumentType())
                .document(entity.getDocument())
                .phone(entity.getPhone())
                .address(entity.getAddress())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .email(entity.getEmail())
                .build();
    }
}
