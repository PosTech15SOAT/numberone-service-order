package br.com.fiap.numberone.vehicle.infrastructure.persistence.mappers;

import br.com.fiap.numberone.vehicle.domain.entities.Vehicle;
import br.com.fiap.numberone.vehicle.infrastructure.persistence.entities.VehicleEntity;
import org.springframework.stereotype.Component;

@Component
public class VehicleEntityMapper {

    public VehicleEntity toEntity(Vehicle domain) {
        return VehicleEntity.builder()
                .id(domain.getId())
                .version(domain.getVersion())
                .licensePlate(domain.getLicensePlate())
                .brand(domain.getBrand())
                .model(domain.getModel())
                .year(domain.getYear())
                .customerId(domain.getCustomerId())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    public Vehicle toDomain(VehicleEntity entity) {
        return Vehicle.builder()
                .id(entity.getId())
                .version(entity.getVersion())
                .licensePlate(entity.getLicensePlate())
                .brand(entity.getBrand())
                .model(entity.getModel())
                .year(entity.getYear())
                .customerId(entity.getCustomerId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
