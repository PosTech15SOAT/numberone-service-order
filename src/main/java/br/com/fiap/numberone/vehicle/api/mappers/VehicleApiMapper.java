package br.com.fiap.numberone.vehicle.api.mappers;

import br.com.fiap.numberone.vehicle.api.dtos.requests.VehicleRequest;
import br.com.fiap.numberone.vehicle.api.dtos.responses.VehicleResponse;
import br.com.fiap.numberone.vehicle.domain.entities.Vehicle;
import org.springframework.stereotype.Component;

@Component
public class VehicleApiMapper {

    public Vehicle toDomain(VehicleRequest request) {
        return Vehicle.builder()
                .licensePlate(request.licensePlate())
                .brand(request.brand())
                .model(request.model())
                .year(request.year())
                .customerId(request.customerId())
                .build();
    }

    public VehicleResponse toResponse(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getLicensePlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getCustomerId(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt()
        );
    }
}
