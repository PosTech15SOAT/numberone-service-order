package br.com.fiap.numberone.customer.infrastructure.config;

import br.com.fiap.numberone.customer.api.mappers.CustomerApiMapper;
import br.com.fiap.numberone.customer.application.gateways.CustomerGateway;
import br.com.fiap.numberone.customer.application.services.CustomerService;
import br.com.fiap.numberone.customer.infrastructure.persistence.gateways.CustomerGatewayImpl;
import br.com.fiap.numberone.customer.infrastructure.persistence.mappers.CustomerEntityMapper;
import br.com.fiap.numberone.customer.infrastructure.persistence.repositories.CustomerRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomerBeansConfig {

    @Bean
    public CustomerGateway customerGateway(
            CustomerRepository customerRepository,
            CustomerEntityMapper customerEntityMapper
    ) {
        return new CustomerGatewayImpl(customerRepository, customerEntityMapper);
    }

    @Bean
    public CustomerService customerService(CustomerGateway customerGateway) {
        return new CustomerService(customerGateway);
    }

    @Bean
    public CustomerApiMapper customerApiMapper() {
        return new CustomerApiMapper();
    }
}
