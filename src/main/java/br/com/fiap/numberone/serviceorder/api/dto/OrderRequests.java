package br.com.fiap.numberone.serviceorder.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public final class OrderRequests {
    private OrderRequests() { }
    public record Create(
            @NotNull @JsonProperty("idCliente") UUID customerId,
            @NotNull @JsonProperty("idVeiculo") UUID vehicleId,
            @NotBlank @Size(max = 2000) @JsonProperty("descricaoInicial") String description,
            @NotBlank @Size(max = 2000) @JsonProperty("descricaoDiagnostico") String diagnosis,
            @Size(max = 2000) @JsonProperty("observacao") String notes,
            @NotNull @JsonProperty("dataHoraEntrada") LocalDateTime entry) { }
    public record Diagnosis(
            @NotBlank @Size(max = 2000) @JsonProperty("descricaoDiagnosticoFinal") String description,
            @NotNull @JsonProperty("dataHoraPrevista") LocalDateTime expected,
            @Size(max = 2000) @JsonProperty("observacao") String notes) { }
    public record Item(
            @NotNull @JsonProperty("idOrdemServico") UUID orderId,
            @NotNull @JsonProperty("idServico") UUID serviceId,
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) @JsonProperty("valor") BigDecimal estimate,
            @Positive @JsonProperty("quantidade") Integer quantity,
            @JsonProperty("opcional") Boolean optional) { }
    public record Material(
            @NotNull @JsonProperty("idMaterial") UUID materialId,
            @Min(1) @JsonProperty("quantidadePrevista") int plannedQuantity) { }
    public record Completion(@PositiveOrZero @JsonProperty("quilometragem") Integer odometerKm) { }
}
