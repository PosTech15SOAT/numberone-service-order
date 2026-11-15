package br.com.fiap.numberone.serviceorder.domain.entities;

import br.com.fiap.numberone.serviceorder.domain.enums.OrderItemStatus;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderRuleException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Builder @AllArgsConstructor
public class ServiceOrderItem {
    private UUID id;
    private UUID automotiveServiceId;
    private String serviceDescription;
    private BigDecimal laborUnitPrice;
    @Builder.Default private int quantity = 1;
    private int estimatedTimeMinutes;
    private boolean optional;
    @Builder.Default private OrderItemStatus status = OrderItemStatus.PENDING;
    private boolean startPending;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private UUID startOperationId;
    @Builder.Default private List<MaterialAllocation> materials = new ArrayList<>();

    public void requestStart(UUID operationId) {
        if (status != OrderItemStatus.PENDING && status != OrderItemStatus.WAITING_FOR_PARTS_AND_SUPPLIES) {
            throw new OrderRuleException("ITEM_NOT_READY_FOR_EXECUTION");
        }
        startPending = true;
        startOperationId = operationId;
    }

    public void insufficientStock() {
        startPending = false;
        status = OrderItemStatus.WAITING_FOR_PARTS_AND_SUPPLIES;
    }

    void confirmStart(LocalDateTime now) {
        if (status != OrderItemStatus.PENDING && status != OrderItemStatus.WAITING_FOR_PARTS_AND_SUPPLIES) {
            throw new OrderRuleException("ITEM_NOT_READY_FOR_EXECUTION");
        }
        requireConfirmedConsumption();
        status = OrderItemStatus.IN_PROGRESS;
        startPending = false;
        startDateTime = now;
    }

    public void complete(LocalDateTime now) {
        if (status != OrderItemStatus.IN_PROGRESS || startDateTime == null) {
            throw new OrderRuleException("ITEM_START_NOT_CONFIRMED");
        }
        if (now.isBefore(startDateTime)) {
            throw new OrderRuleException("INVALID_COMPLETION_TIME");
        }
        requireConfirmedConsumption();
        status = OrderItemStatus.COMPLETED;
        endDateTime = now;
    }

    public void cancelBeforeApproval() {
        requireUnallocatedMaterials();
        if (startPending || (status != OrderItemStatus.PENDING && status != OrderItemStatus.WAITING_FOR_PARTS_AND_SUPPLIES)) {
            throw new OrderRuleException("ITEM_CANCELLATION_REQUIRES_RECONCILIATION");
        }
        status = OrderItemStatus.CANCELLED;
    }

    void confirmCompensatedCancellation() {
        if (startDateTime!=null) { throw new OrderRuleException("ITEM_CANCELLATION_REQUIRES_RECONCILIATION"); }
        startPending=false;
        status=OrderItemStatus.CANCELLED;
    }

    public void requireUnallocatedMaterials() {
        if (materials.stream().anyMatch(MaterialAllocation::hasMaterialEffect)) {
            throw new OrderRuleException("MATERIAL_RECONCILIATION_REQUIRED");
        }
    }

    private void requireConfirmedConsumption() {
        if (!materials.stream().allMatch(MaterialAllocation::hasConfirmedConsumption)) {
            throw new OrderRuleException("MATERIAL_CONSUMPTION_PENDING");
        }
    }

    public BigDecimal estimate() {
        return laborUnitPrice.multiply(BigDecimal.valueOf(quantity)).add(materials.stream()
                .map(m -> m.getReferenceUnitPrice().multiply(BigDecimal.valueOf(m.getPlannedQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
