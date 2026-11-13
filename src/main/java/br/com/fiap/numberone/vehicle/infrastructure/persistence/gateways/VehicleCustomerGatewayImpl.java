package br.com.fiap.numberone.vehicle.infrastructure.persistence.gateways;

import br.com.fiap.numberone.customer.infrastructure.persistence.repositories.CustomerRepository;
import br.com.fiap.numberone.vehicle.application.gateways.CustomerGateway;

import java.util.UUID;

public class VehicleCustomerGatewayImpl implements CustomerGateway {

    private final CustomerRepository customerRepository;

    public VehicleCustomerGatewayImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public boolean existsById(UUID id) {
        return customerRepository.existsById(id);
    }
}
