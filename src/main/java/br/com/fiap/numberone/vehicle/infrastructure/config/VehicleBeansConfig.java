package br.com.fiap.numberone.vehicle.infrastructure.config;

import br.com.fiap.numberone.customer.infrastructure.persistence.repositories.CustomerRepository;
import br.com.fiap.numberone.vehicle.api.mappers.VehicleApiMapper;
import br.com.fiap.numberone.vehicle.application.gateways.CustomerGateway;
import br.com.fiap.numberone.vehicle.application.gateways.VehicleGateway;
import br.com.fiap.numberone.vehicle.application.services.VehicleService;
import br.com.fiap.numberone.vehicle.infrastructure.persistence.gateways.VehicleCustomerGatewayImpl;
import br.com.fiap.numberone.vehicle.infrastructure.persistence.gateways.VehicleGatewayImpl;
import br.com.fiap.numberone.vehicle.infrastructure.persistence.mappers.VehicleEntityMapper;
import br.com.fiap.numberone.vehicle.infrastructure.persistence.repositories.VehicleRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VehicleBeansConfig {

    @Bean
    public VehicleGateway vehicleGateway(
            VehicleRepository vehicleRepository,
            VehicleEntityMapper vehicleEntityMapper
    ) {
        return new VehicleGatewayImpl(vehicleRepository, vehicleEntityMapper);
    }

    @Bean
    public CustomerGateway vehicleCustomerGateway(CustomerRepository customerRepository) {
        return new VehicleCustomerGatewayImpl(customerRepository);
    }

    @Bean
    public VehicleService vehicleService(VehicleGateway vehicleGateway, CustomerGateway vehicleCustomerGateway) {
        return new VehicleService(vehicleGateway, vehicleCustomerGateway);
    }

    @Bean
    public VehicleApiMapper vehicleApiMapper() {
        return new VehicleApiMapper();
    }
}
