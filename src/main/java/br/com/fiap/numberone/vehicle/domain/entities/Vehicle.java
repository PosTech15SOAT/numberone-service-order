package br.com.fiap.numberone.vehicle.domain.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle {

    private UUID id;
    private Long version;
    private String licensePlate;
    private String brand;
    private String model;
    private Integer year;
    private UUID customerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Vehicle updateFrom(Vehicle newVehicle) {
        return Vehicle.builder()
                .id(this.id)
                .version(this.version)
                .licensePlate(newVehicle.licensePlate)
                .brand(newVehicle.brand)
                .model(newVehicle.model)
                .year(newVehicle.year)
                .customerId(newVehicle.customerId)
                .createdAt(this.createdAt)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public Vehicle withNormalizedLicensePlate(String normalizedLicensePlate) {
        return Vehicle.builder()
                .id(this.id)
                .version(this.version)
                .licensePlate(normalizedLicensePlate)
                .brand(this.brand)
                .model(this.model)
                .year(this.year)
                .customerId(this.customerId)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .build();
    }
}
