package br.com.fiap.numberone.serviceorder.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class OrderResponses {
    private OrderResponses() { }
    public record EstimatedTime(UUID idOrdemServico, long tempoEstimadoTotalMinutos, LocalDateTime dataHoraPrevistaSugerida) { }
    public record ExecutionTime(UUID idOrdemServico, long servicosConcluidos, long servicosPendentes,
            long servicosEmExecucao, long servicosCancelados, long servicosAguardandoPecasInsumos,
            long tempoMedioExecucaoMinutos, long servicosComInicioSolicitado) { }
    public record Material(UUID id, UUID idMaterial, String descricao,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal precoUnitarioReferencia,
            int quantidadePrevista, int quantidadeReservada, int quantidadeConsumida,
            UUID reservationId, UUID consumptionOperationId, List<UUID> movementIds) { }
    public record Item(UUID id, UUID idServico, String nomeServico,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valorEstimadoUnitario,
            int quantidade, boolean opcional, String status, String executionState,
            LocalDateTime dataHoraInicio, LocalDateTime dataHoraFim, List<Material> insumos) { }
    public record Estimate(String tipo, String currency,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valorEstimado) { }
    public record Approved(UUID budgetId, int budgetRevision, String currency,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAmount, JsonNode approvedItems) { }
    public record Order(UUID id, UUID idCliente, UUID idVeiculo, UUID branchId, UUID attemptId,
            int budgetRevision, Long version, String status, String executionState,
            String descricaoInicial, String descricaoDiagnostico, String descricaoDiagnosticoFinal, String observacao,
            LocalDateTime dataHoraEntrada, LocalDateTime dataHoraPrevista, LocalDateTime dataHoraEntrega,
            List<Item> itensServico, Estimate estimativa, Approved orcamentoAprovado, boolean pagamentoConfirmado) { }
}
