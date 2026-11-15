package br.com.fiap.numberone.vehicle.api.dtos.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.UUID;

public record VehicleResponse(
        @JsonProperty("id")
        UUID id,
        @JsonProperty("placa")
        String licensePlate,
        @JsonProperty("marca")
        String brand,
        @JsonProperty("modelo")
        String model,
        @JsonProperty("ano")
        Integer year,
        @JsonProperty("idCliente")
        UUID customerId,
        @JsonProperty("criadoEm")
        LocalDateTime createdAt,
        @JsonProperty("atualizadoEm")
        LocalDateTime updatedAt
) {
}
