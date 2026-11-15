package br.com.fiap.numberone.customer.api.controllers;

import br.com.fiap.numberone.customer.api.dtos.requests.CustomerRequest;
import br.com.fiap.numberone.customer.api.dtos.responses.CustomerResponse;
import br.com.fiap.numberone.customer.api.mappers.CustomerApiMapper;
import br.com.fiap.numberone.customer.application.services.CustomerService;
import br.com.fiap.numberone.customer.domain.entities.Customer;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/clientes")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerApiMapper customerApiMapper;

    public CustomerController(CustomerService customerService, CustomerApiMapper customerApiMapper) {
        this.customerService = customerService;
        this.customerApiMapper = customerApiMapper;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@RequestBody @Valid CustomerRequest request) {
        Customer customer = customerService.create(customerApiMapper.toDomain(request));

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(customer.getId())
                .toUri();

        return ResponseEntity.created(location).body(customerApiMapper.toResponse(customer));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> update(@PathVariable UUID id, @RequestBody @Valid CustomerRequest request) {
        Customer updatedCustomer = customerService.update(id, customerApiMapper.toDomain(request));
        return ResponseEntity.ok(customerApiMapper.toResponse(updatedCustomer));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(customerApiMapper.toResponse(customerService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<List<CustomerResponse>> findAll() {
        return ResponseEntity.ok(customerService.findAll()
                .stream()
                .map(customerApiMapper::toResponse)
                .toList());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
