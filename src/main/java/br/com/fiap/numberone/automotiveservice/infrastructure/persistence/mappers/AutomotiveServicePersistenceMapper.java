package br.com.fiap.numberone.automotiveservice.infrastructure.persistence.mappers;

import br.com.fiap.numberone.automotiveservice.domain.entities.AutomotiveService;
import br.com.fiap.numberone.automotiveservice.infrastructure.persistence.entities.AutomotiveServiceEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AutomotiveServicePersistenceMapper {

    AutomotiveServiceEntity toEntity(AutomotiveService domain);

    default AutomotiveService toDomain(AutomotiveServiceEntity entity) {
        if (entity == null) {
            return null;
        }

        return AutomotiveService.restore(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getServiceType(),
                entity.getBaseValue(),
                entity.getEstimatedTimeMinutes(),
                entity.getActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        ).withVersion(entity.getVersion());
    }
}
