package br.com.fiap.numberone.automotiveservice.infrastructure.config;

import br.com.fiap.numberone.automotiveservice.application.gateways.AutomotiveServiceGateway;
import br.com.fiap.numberone.automotiveservice.application.services.AutomotiveServiceService;
import br.com.fiap.numberone.automotiveservice.infrastructure.persistence.gateways.AutoServiceGatewayImpl;
import br.com.fiap.numberone.automotiveservice.infrastructure.persistence.mappers.AutomotiveServicePersistenceMapper;
import br.com.fiap.numberone.automotiveservice.infrastructure.persistence.repositories.AutoServiceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AutoServiceBeansConfig {

    @Bean
    public AutomotiveServiceGateway autoServiceGateway(AutoServiceRepository autoServiceRepository, AutomotiveServicePersistenceMapper autoServiceEntityMapper) {
        return new AutoServiceGatewayImpl(autoServiceRepository, autoServiceEntityMapper);
    }

    @Bean
    public AutomotiveServiceService autoServiceService(AutomotiveServiceGateway autoServiceGateway) {
        return new AutomotiveServiceService(autoServiceGateway);
    }
}
