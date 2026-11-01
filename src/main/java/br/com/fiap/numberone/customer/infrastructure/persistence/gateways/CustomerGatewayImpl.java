package br.com.fiap.numberone.customer.infrastructure.persistence.gateways;

import br.com.fiap.numberone.customer.application.gateways.CustomerGateway;
import br.com.fiap.numberone.customer.domain.entities.Customer;
import br.com.fiap.numberone.customer.infrastructure.persistence.entities.CustomerEntity;
import br.com.fiap.numberone.customer.infrastructure.persistence.mappers.CustomerEntityMapper;
import br.com.fiap.numberone.customer.infrastructure.persistence.repositories.CustomerRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CustomerGatewayImpl implements CustomerGateway {

    private final CustomerRepository customerRepository;
    private final CustomerEntityMapper customerEntityMapper;

    public CustomerGatewayImpl(
            CustomerRepository customerRepository,
            CustomerEntityMapper customerEntityMapper
    ) {
        this.customerRepository = customerRepository;
        this.customerEntityMapper = customerEntityMapper;
    }

    @Override
    public Customer save(Customer customer) {
        CustomerEntity customerEntity = customerEntityMapper.toEntity(customer);
        CustomerEntity savedEntity = customerRepository.save(customerEntity);
        return customerEntityMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return customerRepository.findById(id)
                .map(customerEntityMapper::toDomain);
    }

    @Override
    public List<Customer> findAll() {
        return customerRepository.findAll()
                .stream()
                .map(customerEntityMapper::toDomain)
                .toList();
    }

    @Override
    public void delete(Customer customer) {
        customerRepository.delete(customerEntityMapper.toEntity(customer));
    }

    @Override
    public boolean existsById(UUID id) {
        return customerRepository.existsById(id);
    }
}
