package br.com.fiap.numberone.automotiveservice.unit.domain;

import br.com.fiap.numberone.automotiveservice.domain.entities.AutomotiveService;
import br.com.fiap.numberone.automotiveservice.domain.enums.ServiceType;
import br.com.fiap.numberone.automotiveservice.domain.exceptions.AutomotiveServiceAlreadyActiveException;
import br.com.fiap.numberone.automotiveservice.domain.exceptions.AutomotiveServiceAlreadyInactiveException;
import br.com.fiap.numberone.automotiveservice.domain.exceptions.InvalidAutomotiveServiceDataException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static br.com.fiap.numberone.automotiveservice.support.AutomotiveServiceTestFactory.automotiveService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutomotiveServiceTest {

    @Test
    void shouldCreateActiveAutomotiveServiceWhenDataIsValid() {
        // Given / When
        AutomotiveService service = AutomotiveService.create(
                "REV-001",
                "Revisao completa",
                "Inspecao preventiva completa",
                ServiceType.REVISAO,
                new BigDecimal("350.00"),
                120,
                null
        );

        // Then
        assertThat(service.getId()).isNotNull();
        assertThat(service.getCode()).isEqualTo("REV-001");
        assertThat(service.getActive()).isTrue();
        assertThat(service.getCreatedAt()).isNotNull();
        assertThat(service.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldRejectCreationWithInvalidRequiredData() {
        // Given / When / Then
        assertThatThrownBy(() -> AutomotiveService.create(
                " ",
                "Revisao completa",
                "Inspecao preventiva completa",
                ServiceType.REVISAO,
                new BigDecimal("350.00"),
                120,
                true
        ))
                .isInstanceOf(InvalidAutomotiveServiceDataException.class)
                .hasMessage("O código do serviço é obrigatório");
    }

    @Test
    void shouldRejectCreationWithNegativeBaseValue() {
        // Given / When / Then
        assertThatThrownBy(() -> AutomotiveService.create(
                "REV-001",
                "Revisao completa",
                "Inspecao preventiva completa",
                ServiceType.REVISAO,
                new BigDecimal("-1.00"),
                120,
                true
        ))
                .isInstanceOf(InvalidAutomotiveServiceDataException.class)
                .hasMessage("O valor base do serviço não pode ser negativo");
    }

    @Test
    void shouldRejectCreationWithNonPositiveEstimatedTime() {
        // Given / When / Then
        assertThatThrownBy(() -> AutomotiveService.create(
                "REV-001",
                "Revisao completa",
                "Inspecao preventiva completa",
                ServiceType.REVISAO,
                new BigDecimal("350.00"),
                0,
                true
        ))
                .isInstanceOf(InvalidAutomotiveServiceDataException.class)
                .hasMessage("O tempo estimado em minutos deve ser maior que zero");
    }

    @Test
    void shouldRestoreAutomotiveServiceWithoutRevalidatingPersistenceState() {
        // Given
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 1, 10, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 4, 2, 10, 0);

        // When
        AutomotiveService service = AutomotiveService.restore(
                id,
                "REV-001",
                "Revisao completa",
                "Inspecao preventiva completa",
                ServiceType.REVISAO,
                new BigDecimal("350.00"),
                120,
                false,
                createdAt,
                updatedAt
        );

        // Then
        assertThat(service.getId()).isEqualTo(id);
        assertThat(service.getActive()).isFalse();
        assertThat(service.getCreatedAt()).isEqualTo(createdAt);
        assertThat(service.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void shouldUpdateAutomotiveServiceWhenDataIsValid() {
        // Given
        AutomotiveService service = automotiveService();
        LocalDateTime previousUpdatedAt = service.getUpdatedAt();

        // When
        service.update(
                "ALI-001",
                "Alinhamento",
                "Alinhamento e balanceamento",
                ServiceType.ALINHAMENTO_BALANCEAMENTO,
                new BigDecimal("180.00"),
                60
        );

        // Then
        assertThat(service.getCode()).isEqualTo("ALI-001");
        assertThat(service.getServiceType()).isEqualTo(ServiceType.ALINHAMENTO_BALANCEAMENTO);
        assertThat(service.getBaseValue()).isEqualByComparingTo("180.00");
        assertThat(service.getUpdatedAt()).isAfter(previousUpdatedAt);
    }

    @Test
    void shouldRejectUpdateWithInvalidData() {
        // Given
        AutomotiveService service = automotiveService();

        // When / Then
        assertThatThrownBy(() -> service.update(
                "REV-001",
                " ",
                "Inspecao preventiva completa",
                ServiceType.REVISAO,
                new BigDecimal("350.00"),
                120
        ))
                .isInstanceOf(InvalidAutomotiveServiceDataException.class)
                .hasMessage("O nome do serviço é obrigatório");
    }

    @Test
    void shouldDeactivateActiveAutomotiveService() {
        // Given
        AutomotiveService service = automotiveService();

        // When
        service.deactivate();

        // Then
        assertThat(service.getActive()).isFalse();
    }

    @Test
    void shouldRejectDeactivationWhenAlreadyInactive() {
        // Given
        AutomotiveService service = automotiveService(UUID.randomUUID(), "REV-001", false);

        // When / Then
        assertThatThrownBy(service::deactivate)
                .isInstanceOf(AutomotiveServiceAlreadyInactiveException.class)
                .hasMessage("O serviço automotivo já está inativo");
    }

    @Test
    void shouldActivateInactiveAutomotiveService() {
        // Given
        AutomotiveService service = automotiveService(UUID.randomUUID(), "REV-001", false);

        // When
        service.activate();

        // Then
        assertThat(service.getActive()).isTrue();
    }

    @Test
    void shouldRejectActivationWhenAlreadyActive() {
        // Given
        AutomotiveService service = automotiveService();

        // When / Then
        assertThatThrownBy(service::activate)
                .isInstanceOf(AutomotiveServiceAlreadyActiveException.class)
                .hasMessage("O serviço automotivo já está ativo");
    }
}
