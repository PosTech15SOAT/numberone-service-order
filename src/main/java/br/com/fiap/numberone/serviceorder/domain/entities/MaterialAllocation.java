package br.com.fiap.numberone.serviceorder.domain.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter @Builder @AllArgsConstructor
public class MaterialAllocation {
    private UUID id;
    private UUID materialId;
    private String description;
    private BigDecimal referenceUnitPrice;
    private int plannedQuantity;
    private int reservedQuantity;
    private int consumedQuantity;
    private UUID reservationId;
    private UUID consumptionOperationId;
    @Builder.Default private List<UUID> movementIds = List.of();

    public boolean hasConfirmedConsumption() {
        return plannedQuantity > 0 && consumedQuantity == plannedQuantity
                && reservedQuantity == plannedQuantity && reservationId != null
                && consumptionOperationId != null && !movementIds.isEmpty();
    }
}
