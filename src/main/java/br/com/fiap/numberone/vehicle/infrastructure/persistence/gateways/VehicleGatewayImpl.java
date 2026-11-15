package br.com.fiap.numberone.vehicle.infrastructure.persistence.gateways;

import br.com.fiap.numberone.vehicle.application.gateways.VehicleGateway;
import br.com.fiap.numberone.vehicle.domain.entities.Vehicle;
import br.com.fiap.numberone.vehicle.infrastructure.persistence.entities.VehicleEntity;
import br.com.fiap.numberone.vehicle.infrastructure.persistence.mappers.VehicleEntityMapper;
import br.com.fiap.numberone.vehicle.infrastructure.persistence.repositories.VehicleRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class VehicleGatewayImpl implements VehicleGateway {

    private final VehicleRepository vehicleRepository;
    private final VehicleEntityMapper vehicleEntityMapper;

    public VehicleGatewayImpl(
            VehicleRepository vehicleRepository,
            VehicleEntityMapper vehicleEntityMapper
    ) {
        this.vehicleRepository = vehicleRepository;
        this.vehicleEntityMapper = vehicleEntityMapper;
    }

    @Override
    public Vehicle save(Vehicle vehicle) {
        VehicleEntity vehicleEntity = vehicleEntityMapper.toEntity(vehicle);
        VehicleEntity savedEntity = vehicleRepository.save(vehicleEntity);
        return vehicleEntityMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Vehicle> findById(UUID id) {
        return vehicleRepository.findById(id)
                .map(vehicleEntityMapper::toDomain);
    }

    @Override
    public List<Vehicle> findAll() {
        return vehicleRepository.findAll()
                .stream()
                .map(vehicleEntityMapper::toDomain)
                .toList();
    }

    @Override
    public void delete(Vehicle vehicle) {
        vehicleRepository.delete(vehicleEntityMapper.toEntity(vehicle));
    }

    @Override
    public boolean existsByLicensePlateIgnoreCase(String licensePlate) {
        return vehicleRepository.existsByLicensePlateIgnoreCase(licensePlate);
    }

    @Override
    public boolean existsByLicensePlateIgnoreCaseAndIdNot(String licensePlate, UUID id) {
        return vehicleRepository.existsByLicensePlateIgnoreCaseAndIdNot(licensePlate, id);
    }
}
