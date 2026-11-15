package br.com.fiap.numberone.vehicle.unit.application;

import br.com.fiap.numberone.vehicle.application.gateways.CustomerGateway;
import br.com.fiap.numberone.vehicle.application.gateways.VehicleGateway;
import br.com.fiap.numberone.vehicle.application.services.VehicleService;
import br.com.fiap.numberone.vehicle.domain.entities.Vehicle;
import br.com.fiap.numberone.vehicle.domain.exceptions.VehicleCustomerNotFoundException;
import br.com.fiap.numberone.vehicle.domain.exceptions.VehicleLicensePlateAlreadyExistsException;
import br.com.fiap.numberone.vehicle.domain.exceptions.VehicleNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleGateway vehicleGateway;

    @Mock
    private CustomerGateway customerGateway;

    private VehicleService service;

    @BeforeEach
    void setUp() {
        service = new VehicleService(vehicleGateway, customerGateway);
    }

    @Test
    void shouldCreateVehicleWhenCustomerExistsAndLicensePlateIsUnique() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Vehicle newVehicle = vehicle(null, "abc1d23", customerId);
        Vehicle savedVehicle = vehicle(UUID.randomUUID(), "ABC1D23", customerId);

        when(customerGateway.existsById(customerId)).thenReturn(true);
        when(vehicleGateway.existsByLicensePlateIgnoreCase("ABC1D23")).thenReturn(false);
        when(vehicleGateway.save(any(Vehicle.class))).thenReturn(savedVehicle);

        // Act
        Vehicle result = service.create(newVehicle);

        // Assert
        assertThat(result).isSameAs(savedVehicle);
        verify(vehicleGateway).save(any(Vehicle.class));
    }

    @Test
    void shouldThrowWhenCreatingVehicleForUnknownCustomer() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Vehicle newVehicle = vehicle(null, "ABC1D23", customerId);
        when(customerGateway.existsById(customerId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> service.create(newVehicle))
                .isInstanceOf(VehicleCustomerNotFoundException.class)
                .hasMessage("Cliente nao encontrado para o id: " + customerId);
        verify(vehicleGateway, never()).save(any());
    }

    @Test
    void shouldThrowWhenCreatingVehicleWithDuplicateLicensePlate() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Vehicle newVehicle = vehicle(null, "ABC1D23", customerId);
        when(customerGateway.existsById(customerId)).thenReturn(true);
        when(vehicleGateway.existsByLicensePlateIgnoreCase("ABC1D23")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> service.create(newVehicle))
                .isInstanceOf(VehicleLicensePlateAlreadyExistsException.class)
                .hasMessage("Ja existe um veiculo com a placa informada");
        verify(vehicleGateway, never()).save(any());
    }

    @Test
    void shouldUpdateExistingVehicle() {
        // Arrange
        UUID vehicleId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Vehicle currentVehicle = vehicle(vehicleId, "OLD1A11", customerId);
        Vehicle newData = vehicle(null, "ABC1D23", customerId);
        Vehicle savedVehicle = vehicle(vehicleId, "ABC1D23", customerId);

        when(customerGateway.existsById(customerId)).thenReturn(true);
        when(vehicleGateway.findById(vehicleId)).thenReturn(Optional.of(currentVehicle));
        when(vehicleGateway.existsByLicensePlateIgnoreCaseAndIdNot("ABC1D23", vehicleId)).thenReturn(false);
        when(vehicleGateway.save(any(Vehicle.class))).thenReturn(savedVehicle);

        // Act
        Vehicle result = service.update(vehicleId, newData);

        // Assert
        assertThat(result).isSameAs(savedVehicle);
        verify(vehicleGateway).findById(vehicleId);
        verify(vehicleGateway).save(any(Vehicle.class));
    }

    @Test
    void shouldThrowWhenUpdatingUnknownVehicle() {
        // Arrange
        UUID vehicleId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Vehicle newData = vehicle(null, "ABC1D23", customerId);

        when(customerGateway.existsById(customerId)).thenReturn(true);
        when(vehicleGateway.findById(vehicleId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.update(vehicleId, newData))
                .isInstanceOf(VehicleNotFoundException.class)
                .hasMessage("Veiculo nao encontrado para o id: " + vehicleId);
        verify(vehicleGateway, never()).save(any());
    }

    @Test
    void shouldReturnVehicleById() {
        // Arrange
        UUID vehicleId = UUID.randomUUID();
        Vehicle existingVehicle = vehicle(vehicleId, "ABC1D23", UUID.randomUUID());
        when(vehicleGateway.findById(vehicleId)).thenReturn(Optional.of(existingVehicle));

        // Act
        Vehicle result = service.findById(vehicleId);

        // Assert
        assertThat(result).isSameAs(existingVehicle);
    }

    @Test
    void shouldReturnAllVehicles() {
        // Arrange
        Vehicle firstVehicle = vehicle(UUID.randomUUID(), "ABC1D23", UUID.randomUUID());
        Vehicle secondVehicle = vehicle(UUID.randomUUID(), "DEF2G34", UUID.randomUUID());
        when(vehicleGateway.findAll()).thenReturn(List.of(firstVehicle, secondVehicle));

        // Act
        List<Vehicle> result = service.findAll();

        // Assert
        assertThat(result).containsExactly(firstVehicle, secondVehicle);
    }

    @Test
    void shouldDeleteExistingVehicle() {
        // Arrange
        UUID vehicleId = UUID.randomUUID();
        Vehicle existingVehicle = vehicle(vehicleId, "ABC1D23", UUID.randomUUID());
        when(vehicleGateway.findById(vehicleId)).thenReturn(Optional.of(existingVehicle));

        // Act
        service.delete(vehicleId);

        // Assert
        verify(vehicleGateway).delete(existingVehicle);
    }

    private static Vehicle vehicle(UUID id, String licensePlate, UUID customerId) {
        return Vehicle.builder()
                .id(id)
                .licensePlate(licensePlate)
                .brand("Fiat")
                .model("Argo")
                .year(2023)
                .customerId(customerId)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();
    }
}
