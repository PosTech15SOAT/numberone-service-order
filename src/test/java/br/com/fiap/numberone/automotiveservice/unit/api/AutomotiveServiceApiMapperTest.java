package br.com.fiap.numberone.automotiveservice.unit.api;

import br.com.fiap.numberone.automotiveservice.api.dto.requests.AutomotiveServiceRequest;
import br.com.fiap.numberone.automotiveservice.api.dto.responses.AutomotiveServiceResponse;
import br.com.fiap.numberone.automotiveservice.api.mappers.AutomotiveServiceApiMapper;
import br.com.fiap.numberone.automotiveservice.domain.entities.AutomotiveService;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static br.com.fiap.numberone.automotiveservice.support.AutomotiveServiceTestFactory.automotiveService;
import static br.com.fiap.numberone.automotiveservice.support.AutomotiveServiceTestFactory.request;
import static org.assertj.core.api.Assertions.assertThat;

class AutomotiveServiceApiMapperTest {

    private final AutomotiveServiceApiMapper mapper = Mappers.getMapper(AutomotiveServiceApiMapper.class);

    @Test
    void shouldMapRequestToNewActiveDomain() {
        // Given
        AutomotiveServiceRequest request = request();

        // When
        AutomotiveService domain = mapper.toDomain(request);

        // Then
        assertThat(domain.getId()).isNotNull();
        assertThat(domain.getCode()).isEqualTo("REV-001");
        assertThat(domain.getName()).isEqualTo("Revisao completa");
        assertThat(domain.getDescription()).isEqualTo("Inspecao preventiva completa");
        assertThat(domain.getServiceType()).isEqualTo(request.getServiceType());
        assertThat(domain.getBaseValue()).isEqualByComparingTo(request.getBaseValue());
        assertThat(domain.getEstimatedTimeMinutes()).isEqualTo(120);
        assertThat(domain.getActive()).isTrue();
    }

    @Test
    void shouldReturnNullDomainWhenRequestIsNull() {
        // Given / When
        AutomotiveService domain = mapper.toDomain(null);

        // Then
        assertThat(domain).isNull();
    }

    @Test
    void shouldMapDomainToResponse() {
        // Given
        AutomotiveService domain = automotiveService();

        // When
        AutomotiveServiceResponse response = mapper.toResponse(domain);

        // Then
        assertThat(response.getId()).isEqualTo(domain.getId());
        assertThat(response.getCode()).isEqualTo(domain.getCode());
        assertThat(response.getName()).isEqualTo(domain.getName());
        assertThat(response.getDescription()).isEqualTo(domain.getDescription());
        assertThat(response.getServiceType()).isEqualTo(domain.getServiceType());
        assertThat(response.getBaseValue()).isEqualByComparingTo(domain.getBaseValue());
        assertThat(response.getEstimatedTimeMinutes()).isEqualTo(domain.getEstimatedTimeMinutes());
        assertThat(response.getActive()).isEqualTo(domain.getActive());
        assertThat(response.getCreatedAt()).isEqualTo(domain.getCreatedAt());
        assertThat(response.getUpdatedAt()).isEqualTo(domain.getUpdatedAt());
    }
}
