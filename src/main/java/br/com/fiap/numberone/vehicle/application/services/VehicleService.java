package br.com.fiap.numberone.vehicle.application.services;

import br.com.fiap.numberone.vehicle.application.gateways.CustomerGateway;
import br.com.fiap.numberone.vehicle.application.gateways.VehicleGateway;
import br.com.fiap.numberone.vehicle.domain.entities.Vehicle;
import br.com.fiap.numberone.vehicle.domain.exceptions.VehicleCustomerNotFoundException;
import br.com.fiap.numberone.vehicle.domain.exceptions.VehicleLicensePlateAlreadyExistsException;
import br.com.fiap.numberone.vehicle.domain.exceptions.VehicleNotFoundException;

import java.util.List;
import java.util.UUID;

@org.springframework.transaction.annotation.Transactional
public class VehicleService {

    private final VehicleGateway vehicleGateway;
    private final CustomerGateway customerGateway;

    public VehicleService(VehicleGateway vehicleGateway, CustomerGateway customerGateway) {
        this.vehicleGateway = vehicleGateway;
        this.customerGateway = customerGateway;
    }

    public Vehicle create(Vehicle vehicle) {
        validateCustomerExists(vehicle.getCustomerId());

        String normalizedLicensePlate = normalizeLicensePlate(vehicle.getLicensePlate());
        validateLicensePlateDoesNotExist(normalizedLicensePlate);

        return vehicleGateway.save(vehicle.withNormalizedLicensePlate(normalizedLicensePlate));
    }

    public Vehicle update(UUID id, Vehicle newData) {
        validateCustomerExists(newData.getCustomerId());

        Vehicle currentVehicle = findById(id);
        String normalizedLicensePlate = normalizeLicensePlate(newData.getLicensePlate());
        validateLicensePlateDoesNotExistForOtherVehicle(normalizedLicensePlate, id);

        Vehicle updatedVehicle = currentVehicle.updateFrom(newData.withNormalizedLicensePlate(normalizedLicensePlate));
        return vehicleGateway.save(updatedVehicle);
    }

    public Vehicle findById(UUID id) {
        return vehicleGateway.findById(id)
                .orElseThrow(() -> new VehicleNotFoundException("Veiculo nao encontrado para o id: " + id));
    }

    public List<Vehicle> findAll() {
        return vehicleGateway.findAll();
    }

    public void delete(UUID id) {
        Vehicle vehicle = findById(id);
        vehicleGateway.delete(vehicle);
    }

    private void validateCustomerExists(UUID customerId) {
        if (!customerGateway.existsById(customerId)) {
            throw new VehicleCustomerNotFoundException("Cliente nao encontrado para o id: " + customerId);
        }
    }

    private void validateLicensePlateDoesNotExist(String licensePlate) {
        if (vehicleGateway.existsByLicensePlateIgnoreCase(licensePlate)) {
            throw new VehicleLicensePlateAlreadyExistsException("Ja existe um veiculo com a placa informada");
        }
    }

    private void validateLicensePlateDoesNotExistForOtherVehicle(String licensePlate, UUID vehicleId) {
        if (vehicleGateway.existsByLicensePlateIgnoreCaseAndIdNot(licensePlate, vehicleId)) {
            throw new VehicleLicensePlateAlreadyExistsException("Ja existe outro veiculo com a placa informada");
        }
    }

    private String normalizeLicensePlate(String licensePlate) {
        return licensePlate == null ? null : licensePlate.trim().toUpperCase();
    }
}
