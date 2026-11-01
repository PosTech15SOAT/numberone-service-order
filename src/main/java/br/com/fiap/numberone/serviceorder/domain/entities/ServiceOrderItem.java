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
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    @Builder.Default private List<MaterialAllocation> materials = new ArrayList<>();

    public void complete(LocalDateTime now) {
        if (status != OrderItemStatus.IN_PROGRESS || startDateTime == null) {
            throw new OrderRuleException("ITEM_START_NOT_CONFIRMED");
        }
        if (!materials.stream().allMatch(MaterialAllocation::hasConfirmedConsumption)) {
            throw new OrderRuleException("MATERIAL_CONSUMPTION_PENDING");
        }
        status = OrderItemStatus.COMPLETED;
        endDateTime = now;
    }

    public void cancelBeforeApproval() {
        requireUnallocatedMaterials();
        if (status != OrderItemStatus.PENDING && status != OrderItemStatus.WAITING_FOR_PARTS_AND_SUPPLIES) {
            throw new OrderRuleException("ITEM_CANCELLATION_REQUIRES_RECONCILIATION");
        }
        status = OrderItemStatus.CANCELLED;
    }

    public void requireUnallocatedMaterials() {
        if (materials.stream().anyMatch(m -> m.getReservedQuantity() != 0 || m.getConsumedQuantity() != 0)) {
            throw new OrderRuleException("MATERIAL_RECONCILIATION_REQUIRED");
        }
    }

    public BigDecimal estimate() {
        return laborUnitPrice.multiply(BigDecimal.valueOf(quantity)).add(materials.stream()
                .map(m -> m.getReferenceUnitPrice().multiply(BigDecimal.valueOf(m.getPlannedQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
