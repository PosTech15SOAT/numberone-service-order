package br.com.fiap.numberone.serviceorder.api.mappers;

import br.com.fiap.numberone.serviceorder.api.dto.OrderResponses.*;
import br.com.fiap.numberone.serviceorder.domain.entities.*;
import br.com.fiap.numberone.serviceorder.domain.enums.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ServiceOrderApiMapper {
    private final ObjectMapper json;
    public ServiceOrderApiMapper(ObjectMapper json) { this.json = json; }
    public Order toResponse(ServiceOrder o) {
        Approved approved = o.getApprovedBudgetId() == null ? null : new Approved(o.getApprovedBudgetId(),
                o.getApprovedRevision(), o.getApprovedCurrency(), o.getApprovedAmount(), json.readTree(o.getApprovedItemsJson()));
        return new Order(o.getId(), o.getCustomerId(), o.getVehicleId(), o.getBranchId(), o.getAttemptId(),
                o.getBudgetRevision(), o.getVersion(), status(o.getStatus()), o.getStatus().name(), o.getInitialDescription(),
                o.getDiagnosisDescription(), o.getFinalDiagnosisDescription(), o.getNotes(), o.getEntryDateTime(),
                o.getExpectedDateTime(), o.getDeliveryDateTime(), o.getItems().stream().map(this::toResponse).toList(),
                new Estimate("ESTIMATIVA", "BRL", o.estimate()), approved, o.getPaymentConfirmedAt() != null);
    }
    public Item toResponse(ServiceOrderItem i) {
        return new Item(i.getId(), i.getAutomotiveServiceId(), i.getServiceDescription(), i.getLaborUnitPrice(),
                i.getQuantity(), i.isOptional(), status(i.getStatus()), i.getStatus().name(), i.getStartDateTime(),
                i.getEndDateTime(), i.getMaterials().stream().map(this::toResponse).toList());
    }
    public Material toResponse(MaterialAllocation m) {
        return new Material(m.getId(), m.getMaterialId(), m.getDescription(), m.getReferenceUnitPrice(),
                m.getPlannedQuantity(), m.getReservedQuantity(), m.getConsumedQuantity(), m.getReservationId(),
                m.getConsumptionOperationId(), m.getMovementIds());
    }
    public String status(ServiceOrderStatus status) {
        return switch (status) {
            case RECEIVED -> "RECEBIDA"; case IN_DIAGNOSIS -> "EM_DIAGNOSTICO";
            case WAITING_APPROVAL -> "AGUARDANDO_APROVACAO"; case APPROVED -> "APROVADA";
            case REJECTED -> "REJEITADA"; case READY_FOR_EXECUTION -> "PRONTA_PARA_EXECUCAO";
            case IN_PROGRESS -> "EM_EXECUCAO"; case COMPLETED -> "FINALIZADA";
            case CANCELLED -> "CANCELADA"; case DELIVERED -> "ENTREGUE";
        };
    }
    public String status(OrderItemStatus status) {
        return switch (status) {
            case PENDING -> "PENDENTE"; case WAITING_FOR_PARTS_AND_SUPPLIES -> "AGUARDANDO_PECAS_E_INSUMOS";
            case START_REQUESTED -> "INICIO_SOLICITADO"; case IN_PROGRESS -> "EM_EXECUCAO";
            case COMPLETED -> "FINALIZADO"; case CANCELLED -> "CANCELADO";
        };
    }
}
