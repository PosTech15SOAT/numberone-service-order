package br.com.fiap.numberone.automotiveservice.unit.application;

import br.com.fiap.numberone.automotiveservice.application.gateways.AutomotiveServiceGateway;
import br.com.fiap.numberone.automotiveservice.application.services.AutomotiveServiceService;
import br.com.fiap.numberone.automotiveservice.domain.entities.AutomotiveService;
import br.com.fiap.numberone.automotiveservice.domain.exceptions.AutoServiceNotFoundException;
import br.com.fiap.numberone.automotiveservice.domain.exceptions.AutomotiveServiceBusinessException;
import br.com.fiap.numberone.shared.application.gateways.LoggerGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static br.com.fiap.numberone.automotiveservice.support.AutomotiveServiceTestFactory.automotiveService;
import static br.com.fiap.numberone.automotiveservice.support.AutomotiveServiceTestFactory.newAutomotiveService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomotiveServiceServiceTest {

    @Mock
    private AutomotiveServiceGateway gateway;

    @Mock
    private LoggerGateway logger;

    private AutomotiveServiceService service;

    @BeforeEach
    void setUp() {
        service = new AutomotiveServiceService(gateway, logger);
    }

    @Test
    void shouldCreateAutomotiveServiceWhenCodeIsUnique() {
        // Given
        AutomotiveService newService = newAutomotiveService("ALI-001");
        AutomotiveService savedService = automotiveService(UUID.randomUUID(), "ALI-001", true);
        when(gateway.existsByCode("ALI-001")).thenReturn(false);
        when(gateway.save(newService)).thenReturn(savedService);

        // When
        AutomotiveService result = service.create(newService);

        // Then
        assertThat(result).isSameAs(savedService);
        verify(gateway).existsByCode("ALI-001");
        verify(gateway).save(newService);
    }

    @Test
    void shouldRejectCreationWhenCodeAlreadyExists() {
        // Given
        AutomotiveService newService = newAutomotiveService("ALI-001");
        when(gateway.existsByCode("ALI-001")).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> service.create(newService))
                .isInstanceOf(AutomotiveServiceBusinessException.class)
                .hasMessage("Já existe um serviço automotivo com o código informado");
        verify(gateway, never()).save(any());
    }

    @Test
    void shouldUpdateExistingAutomotiveServiceWhenCodeIsAvailable() {
        // Given
        UUID id = UUID.randomUUID();
        AutomotiveService current = automotiveService(id, "REV-001", true);
        AutomotiveService newData = newAutomotiveService("ALI-001");
        when(gateway.findById(id)).thenReturn(Optional.of(current));
        when(gateway.findByCode("ALI-001")).thenReturn(Optional.empty());
        when(gateway.save(any(AutomotiveService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AutomotiveService result = service.update(id, newData);

        // Then
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getCode()).isEqualTo("ALI-001");
        ArgumentCaptor<AutomotiveService> captor = ArgumentCaptor.forClass(AutomotiveService.class);
        verify(gateway).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Alinhamento");
    }

    @Test
    void shouldRejectUpdateWhenAutomotiveServiceDoesNotExist() {
        // Given
        UUID id = UUID.randomUUID();
        when(gateway.findById(id)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.update(id, newAutomotiveService("ALI-001")))
                .isInstanceOf(AutoServiceNotFoundException.class)
                .hasMessage("Serviço automotivo não encontrado");
        verify(gateway, never()).save(any());
    }

    @Test
    void shouldRejectUpdateWhenCodeBelongsToAnotherAutomotiveService() {
        // Given
        UUID id = UUID.randomUUID();
        UUID anotherId = UUID.randomUUID();
        when(gateway.findById(id)).thenReturn(Optional.of(automotiveService(id, "REV-001", true)));
        when(gateway.findByCode("ALI-001")).thenReturn(Optional.of(automotiveService(anotherId, "ALI-001", true)));

        // When / Then
        assertThatThrownBy(() -> service.update(id, newAutomotiveService("ALI-001")))
                .isInstanceOf(AutomotiveServiceBusinessException.class)
                .hasMessage("Já existe outro serviço automotivo com o código informado");
        verify(gateway, never()).save(any());
    }

    @Test
    void shouldFindAllActiveAutomotiveServices() {
        // Given
        List<AutomotiveService> services = List.of(automotiveService(), automotiveService(UUID.randomUUID(), "ALI-001", true));
        when(gateway.findAllActive()).thenReturn(services);

        // When
        List<AutomotiveService> result = service.findAll();

        // Then
        assertThat(result).containsExactlyElementsOf(services);
        verify(logger).info("Buscando todos os serviços");
    }

    @Test
    void shouldFindAutomotiveServiceById() {
        // Given
        UUID id = UUID.randomUUID();
        AutomotiveService existing = automotiveService(id, "REV-001", true);
        when(gateway.findById(id)).thenReturn(Optional.of(existing));

        // When
        AutomotiveService result = service.findById(id);

        // Then
        assertThat(result).isSameAs(existing);
        verify(logger).info("Buscando serviço automotivo {}", id);
    }

    @Test
    void shouldRejectFindByIdWhenAutomotiveServiceDoesNotExist() {
        // Given
        UUID id = UUID.randomUUID();
        when(gateway.findById(id)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(AutoServiceNotFoundException.class)
                .hasMessage("Serviço automotivo não encontrado");
    }

    @Test
    void shouldInactivateAutomotiveService() {
        // Given
        UUID id = UUID.randomUUID();
        AutomotiveService existing = automotiveService(id, "REV-001", true);
        when(gateway.findById(id)).thenReturn(Optional.of(existing));

        // When
        service.inactivate(id);

        // Then
        assertThat(existing.getActive()).isFalse();
        verify(gateway).save(existing);
    }

    @Test
    void shouldActivateAutomotiveService() {
        // Given
        UUID id = UUID.randomUUID();
        AutomotiveService existing = automotiveService(id, "REV-001", false);
        when(gateway.findById(id)).thenReturn(Optional.of(existing));

        // When
        service.activate(id);

        // Then
        assertThat(existing.getActive()).isTrue();
        verify(gateway).save(existing);
    }
}
