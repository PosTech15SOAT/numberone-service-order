package br.com.fiap.numberone.automotiveservice.application.services;

import br.com.fiap.numberone.automotiveservice.application.gateways.AutomotiveServiceGateway;
import br.com.fiap.numberone.automotiveservice.domain.entities.AutomotiveService;
import br.com.fiap.numberone.automotiveservice.domain.exceptions.AutoServiceNotFoundException;
import br.com.fiap.numberone.automotiveservice.domain.exceptions.AutomotiveServiceBusinessException;
import br.com.fiap.numberone.shared.application.gateways.LoggerGateway;

import java.util.List;
import java.util.UUID;

@org.springframework.transaction.annotation.Transactional
public class AutomotiveServiceService {

    private final AutomotiveServiceGateway autoServiceGateway;
    private final LoggerGateway logger;

    public AutomotiveServiceService(AutomotiveServiceGateway autoServiceGateway, LoggerGateway loggerGateway) {
        this.autoServiceGateway = autoServiceGateway;
        this.logger = loggerGateway;
    }

    public AutomotiveService create(AutomotiveService autoService) {
        validateUniqueCodeForCreate(autoService.getCode());
        return autoServiceGateway.save(autoService);
    }

    public AutomotiveService update(UUID id, AutomotiveService newData) {
        AutomotiveService currentAutoService = autoServiceGateway.findById(id)
                .orElseThrow(() -> new AutoServiceNotFoundException("Serviço automotivo não encontrado"));

        validateUniqueCodeForUpdate(id, newData.getCode());

        currentAutoService.update(
                newData.getCode(),
                newData.getName(),
                newData.getDescription(),
                newData.getServiceType(),
                newData.getBaseValue(),
                newData.getEstimatedTimeMinutes()
        );

        return autoServiceGateway.save(currentAutoService);
    }

    public List<AutomotiveService> findAll() {
        logger.info("Buscando todos os serviços");
        return autoServiceGateway.findAllActive();
    }

    public AutomotiveService findById(UUID id) {
        logger.info("Buscando serviço automotivo {}", id);
        return autoServiceGateway.findById(id)
                .orElseThrow(() -> new AutoServiceNotFoundException("Serviço automotivo não encontrado"));
    }

    public void inactivate(UUID id) {
        AutomotiveService autoService = autoServiceGateway.findById(id)
                .orElseThrow(() -> new AutoServiceNotFoundException("Serviço automotivo não encontrado"));

        autoService.deactivate();

        autoServiceGateway.save(autoService);
    }

    public void activate(UUID id) {
        AutomotiveService autoService = autoServiceGateway.findById(id)
                .orElseThrow(() -> new AutoServiceNotFoundException("Serviço automotivo não encontrado"));

        autoService.activate();

        autoServiceGateway.save(autoService);
    }

    private void validateUniqueCodeForCreate(String code) {
        if (autoServiceGateway.existsByCode(code)) {
            throw new AutomotiveServiceBusinessException("Já existe um serviço automotivo com o código informado");
        }
    }

    private void validateUniqueCodeForUpdate(UUID id, String code) {
        autoServiceGateway.findByCode(code)
                .filter(found -> !found.getId().equals(id))
                .ifPresent(found -> {
                    throw new AutomotiveServiceBusinessException("Já existe outro serviço automotivo com o código informado");
                });
    }
}
