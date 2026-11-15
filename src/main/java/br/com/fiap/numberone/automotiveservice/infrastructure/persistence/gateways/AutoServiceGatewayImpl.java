package br.com.fiap.numberone.automotiveservice.infrastructure.persistence.gateways;

import br.com.fiap.numberone.automotiveservice.application.gateways.AutomotiveServiceGateway;
import br.com.fiap.numberone.automotiveservice.domain.entities.AutomotiveService;
import br.com.fiap.numberone.automotiveservice.infrastructure.persistence.entities.AutomotiveServiceEntity;
import br.com.fiap.numberone.automotiveservice.infrastructure.persistence.mappers.AutomotiveServicePersistenceMapper;
import br.com.fiap.numberone.automotiveservice.infrastructure.persistence.repositories.AutoServiceRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AutoServiceGatewayImpl implements AutomotiveServiceGateway {

    private final AutoServiceRepository repository;
    private final AutomotiveServicePersistenceMapper mapper;

    public AutoServiceGatewayImpl(
            AutoServiceRepository repository,
            AutomotiveServicePersistenceMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public AutomotiveService save(AutomotiveService automotiveService) {
        AutomotiveServiceEntity entity = mapper.toEntity(automotiveService);
        AutomotiveServiceEntity savedEntity = repository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public Optional<AutomotiveService> findById(UUID id) {
        return repository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<AutomotiveService> findByCode(String code) {
        return repository.findByCode(code)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    @Override
    public List<AutomotiveService> findAllActive() {
        return repository.findByActiveTrue()
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
