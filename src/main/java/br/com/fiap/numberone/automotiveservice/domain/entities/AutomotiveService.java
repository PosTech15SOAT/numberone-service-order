package br.com.fiap.numberone.automotiveservice.domain.entities;

import br.com.fiap.numberone.automotiveservice.domain.enums.ServiceType;
import br.com.fiap.numberone.automotiveservice.domain.exceptions.AutomotiveServiceAlreadyActiveException;
import br.com.fiap.numberone.automotiveservice.domain.exceptions.AutomotiveServiceAlreadyInactiveException;
import br.com.fiap.numberone.automotiveservice.domain.exceptions.InvalidAutomotiveServiceDataException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class AutomotiveService {

    private UUID id;
    private Long version;
    private String code;
    private String name;
    private String description;
    private ServiceType serviceType;
    private BigDecimal baseValue;
    private Integer estimatedTimeMinutes;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private AutomotiveService() {
    }

    public static AutomotiveService create(
            String code,
            String name,
            String description,
            ServiceType serviceType,
            BigDecimal baseValue,
            Integer estimatedTimeMinutes,
            Boolean active
    ) {
        return buildNew(
                UUID.randomUUID(),
                code,
                name,
                description,
                serviceType,
                baseValue,
                estimatedTimeMinutes,
                active != null ? active : true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    public static AutomotiveService restore(
            UUID id,
            String code,
            String name,
            String description,
            ServiceType serviceType,
            BigDecimal baseValue,
            Integer estimatedTimeMinutes,
            Boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        AutomotiveService automotiveService = new AutomotiveService();
        automotiveService.id = id;
        automotiveService.code = code;
        automotiveService.name = name;
        automotiveService.description = description;
        automotiveService.serviceType = serviceType;
        automotiveService.baseValue = baseValue;
        automotiveService.estimatedTimeMinutes = estimatedTimeMinutes;
        automotiveService.active = active;
        automotiveService.createdAt = createdAt;
        automotiveService.updatedAt = updatedAt;
        return automotiveService;
    }

    private static AutomotiveService buildNew(
            UUID id,
            String code,
            String name,
            String description,
            ServiceType serviceType,
            BigDecimal baseValue,
            Integer estimatedTimeMinutes,
            Boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        if (id == null) {
            throw new InvalidAutomotiveServiceDataException("O id do serviço é obrigatório");
        }

        if (code == null || code.isBlank()) {
            throw new InvalidAutomotiveServiceDataException("O código do serviço é obrigatório");
        }

        if (name == null || name.isBlank()) {
            throw new InvalidAutomotiveServiceDataException("O nome do serviço é obrigatório");
        }

        if (description == null || description.isBlank()) {
            throw new InvalidAutomotiveServiceDataException("A descrição do serviço é obrigatória");
        }

        if (serviceType == null) {
            throw new InvalidAutomotiveServiceDataException("O tipo do serviço é obrigatório");
        }

        if (baseValue == null) {
            throw new InvalidAutomotiveServiceDataException("O valor base do serviço é obrigatório");
        }

        if (baseValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAutomotiveServiceDataException("O valor base do serviço não pode ser negativo");
        }

        if (estimatedTimeMinutes == null) {
            throw new InvalidAutomotiveServiceDataException("O tempo estimado em minutos é obrigatório");
        }

        if (estimatedTimeMinutes <= 0) {
            throw new InvalidAutomotiveServiceDataException("O tempo estimado em minutos deve ser maior que zero");
        }

        if (active == null) {
            throw new InvalidAutomotiveServiceDataException("O status ativo do serviço é obrigatório");
        }

        if (createdAt == null) {
            throw new InvalidAutomotiveServiceDataException("A data de criação do serviço é obrigatória");
        }

        if (updatedAt == null) {
            throw new InvalidAutomotiveServiceDataException("A data de atualização do serviço é obrigatória");
        }

        AutomotiveService automotiveService = new AutomotiveService();
        automotiveService.id = id;
        automotiveService.code = code;
        automotiveService.name = name;
        automotiveService.description = description;
        automotiveService.serviceType = serviceType;
        automotiveService.baseValue = baseValue;
        automotiveService.estimatedTimeMinutes = estimatedTimeMinutes;
        automotiveService.active = active;
        automotiveService.createdAt = createdAt;
        automotiveService.updatedAt = updatedAt;
        return automotiveService;
    }

    public void update(
            String code,
            String name,
            String description,
            ServiceType serviceType,
            BigDecimal baseValue,
            Integer estimatedTimeMinutes
    ) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.serviceType = serviceType;
        this.baseValue = baseValue;
        this.estimatedTimeMinutes = estimatedTimeMinutes;
        this.updatedAt = LocalDateTime.now();

        validateForUpdate();
    }

    public void deactivate() {
        if (Boolean.FALSE.equals(this.active)) {
            throw new AutomotiveServiceAlreadyInactiveException("O serviço automotivo já está inativo");
        }

        this.active = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void activate() {
        if (Boolean.TRUE.equals(this.active)) {
            throw new AutomotiveServiceAlreadyActiveException("O serviço automotivo já está ativo");
        }

        this.active = true;
        this.updatedAt = LocalDateTime.now();
    }

    private void validateForUpdate() {
        if (code == null || code.isBlank()) {
            throw new InvalidAutomotiveServiceDataException("O código do serviço é obrigatório");
        }

        if (name == null || name.isBlank()) {
            throw new InvalidAutomotiveServiceDataException("O nome do serviço é obrigatório");
        }

        if (description == null || description.isBlank()) {
            throw new InvalidAutomotiveServiceDataException("A descrição do serviço é obrigatória");
        }

        if (serviceType == null) {
            throw new InvalidAutomotiveServiceDataException("O tipo do serviço é obrigatório");
        }

        if (baseValue == null) {
            throw new InvalidAutomotiveServiceDataException("O valor base do serviço é obrigatório");
        }

        if (baseValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAutomotiveServiceDataException("O valor base do serviço não pode ser negativo");
        }

        if (estimatedTimeMinutes == null) {
            throw new InvalidAutomotiveServiceDataException("O tempo estimado em minutos é obrigatório");
        }

        if (estimatedTimeMinutes <= 0) {
            throw new InvalidAutomotiveServiceDataException("O tempo estimado em minutos deve ser maior que zero");
        }
    }

    public Long getVersion() { return version; }

    public AutomotiveService withVersion(Long version) {
        this.version = version;
        return this;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public BigDecimal getBaseValue() {
        return baseValue;
    }

    public Integer getEstimatedTimeMinutes() {
        return estimatedTimeMinutes;
    }

    public Boolean getActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
