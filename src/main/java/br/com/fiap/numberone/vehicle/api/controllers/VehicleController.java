package br.com.fiap.numberone.vehicle.api.controllers;

import br.com.fiap.numberone.vehicle.api.dtos.requests.VehicleRequest;
import br.com.fiap.numberone.vehicle.api.dtos.responses.VehicleResponse;
import br.com.fiap.numberone.vehicle.api.mappers.VehicleApiMapper;
import br.com.fiap.numberone.vehicle.application.services.VehicleService;
import br.com.fiap.numberone.vehicle.domain.entities.Vehicle;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/veiculos")
public class VehicleController {

    private final VehicleService vehicleService;
    private final VehicleApiMapper vehicleApiMapper;

    public VehicleController(VehicleService vehicleService, VehicleApiMapper vehicleApiMapper) {
        this.vehicleService = vehicleService;
        this.vehicleApiMapper = vehicleApiMapper;
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> create(@RequestBody @Valid VehicleRequest request) {
        Vehicle vehicle = vehicleService.create(vehicleApiMapper.toDomain(request));

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(vehicle.getId())
                .toUri();

        return ResponseEntity.created(location).body(vehicleApiMapper.toResponse(vehicle));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VehicleResponse> update(@PathVariable UUID id, @RequestBody @Valid VehicleRequest request) {
        Vehicle updatedVehicle = vehicleService.update(id, vehicleApiMapper.toDomain(request));
        return ResponseEntity.ok(vehicleApiMapper.toResponse(updatedVehicle));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(vehicleApiMapper.toResponse(vehicleService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<List<VehicleResponse>> findAll() {
        return ResponseEntity.ok(vehicleService.findAll()
                .stream()
                .map(vehicleApiMapper::toResponse)
                .toList());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        vehicleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
