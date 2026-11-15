package br.com.fiap.numberone.customer.api.mappers;

import br.com.fiap.numberone.customer.api.dtos.requests.CustomerRequest;
import br.com.fiap.numberone.customer.api.dtos.responses.CustomerResponse;
import br.com.fiap.numberone.customer.domain.entities.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerApiMapper {

    public Customer toDomain(CustomerRequest request) {
        return Customer.builder()
                .name(request.name())
                .documentType(request.documentType())
                .document(request.document())
                .email(request.email())
                .phone(request.phone())
                .address(request.address())
                .active(request.active() != null ? request.active() : Boolean.TRUE)
                .build();
    }

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getDocumentType(),
                customer.getDocument(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getActive(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
