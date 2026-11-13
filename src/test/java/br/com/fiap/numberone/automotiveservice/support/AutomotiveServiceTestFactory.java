package br.com.fiap.numberone.automotiveservice.support;

import br.com.fiap.numberone.automotiveservice.api.dto.requests.AutomotiveServiceRequest;
import br.com.fiap.numberone.automotiveservice.domain.entities.AutomotiveService;
import br.com.fiap.numberone.automotiveservice.domain.enums.ServiceType;
import br.com.fiap.numberone.automotiveservice.infrastructure.persistence.entities.AutomotiveServiceEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public final class AutomotiveServiceTestFactory {

    private AutomotiveServiceTestFactory() {
    }

    public static AutomotiveService automotiveService() {
        return automotiveService(UUID.fromString("11111111-1111-1111-1111-111111111111"), "REV-001", true);
    }

    public static AutomotiveService automotiveService(UUID id, String code, boolean active) {
        return AutomotiveService.restore(
                id,
                code,
                "Revisao completa",
                "Inspecao preventiva completa",
                ServiceType.REVISAO,
                new BigDecimal("350.00"),
                120,
                active,
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 10, 0)
        );
    }

    public static AutomotiveService newAutomotiveService(String code) {
        return AutomotiveService.create(
                code,
                "Alinhamento",
                "Alinhamento e balanceamento",
                ServiceType.ALINHAMENTO_BALANCEAMENTO,
                new BigDecimal("180.00"),
                60,
                true
        );
    }

    public static AutomotiveServiceRequest request() {
        return new AutomotiveServiceRequest(
                "REV-001",
                "Revisao completa",
                "Inspecao preventiva completa",
                ServiceType.REVISAO,
                new BigDecimal("350.00"),
                120
        );
    }

    public static AutomotiveServiceEntity entity(UUID id, String code, boolean active) {
        return new AutomotiveServiceEntity(
                id,
                null,
                code,
                "Revisao completa",
                "Inspecao preventiva completa",
                ServiceType.REVISAO,
                new BigDecimal("350.00"),
                120,
                active,
                LocalDateTime.of(2026, 4, 1, 10, 0),
                LocalDateTime.of(2026, 4, 1, 10, 0)
        );
    }
}
