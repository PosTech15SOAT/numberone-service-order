package br.com.fiap.numberone.automotiveservice.application.gateways;

import br.com.fiap.numberone.automotiveservice.domain.entities.AutomotiveService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutomotiveServiceGateway {
    AutomotiveService save(AutomotiveService automotiveService);
    Optional<AutomotiveService> findById(UUID id);
    Optional<AutomotiveService> findByCode(String code);
    boolean existsByCode(String code);
    List<AutomotiveService> findAllActive();
}
