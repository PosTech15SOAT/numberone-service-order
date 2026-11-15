package br.com.fiap.numberone.automotiveservice.api.mappers;

import br.com.fiap.numberone.automotiveservice.api.dto.requests.AutomotiveServiceRequest;
import br.com.fiap.numberone.automotiveservice.api.dto.responses.AutomotiveServiceResponse;
import br.com.fiap.numberone.automotiveservice.domain.entities.AutomotiveService;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AutomotiveServiceApiMapper {

    default AutomotiveService toDomain(AutomotiveServiceRequest request) {
        if (request == null) {
            return null;
        }

        return AutomotiveService.create(
                request.getCode(),
                request.getName(),
                request.getDescription(),
                request.getServiceType(),
                request.getBaseValue(),
                request.getEstimatedTimeMinutes(),
                true
        );
    }

    AutomotiveServiceResponse toResponse(AutomotiveService domain);
}
