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

    public void confirmConsumption(UUID operation, List<UUID> movements) {
        if (!validReference(operation) || movements == null || movements.isEmpty()
                || movements.stream().anyMatch(m -> !validReference(m))) {
            throw new br.com.fiap.numberone.serviceorder.domain.exceptions.OrderRuleException("INVALID_CONSUMPTION_RESULT");
        }
        consumptionOperationId = operation;
        movementIds = List.copyOf(movements);
        consumedQuantity = plannedQuantity;
    }

    public boolean hasConfirmedConsumption() {
        return plannedQuantity > 0 && consumedQuantity == plannedQuantity
                && validReference(consumptionOperationId) && movementIds != null && !movementIds.isEmpty()
                && movementIds.stream().allMatch(MaterialAllocation::validReference);
    }

    private static boolean validReference(UUID reference) {
        return reference != null && (reference.getMostSignificantBits() != 0 || reference.getLeastSignificantBits() != 0);
    }

    public boolean hasMaterialEffect() {
        return reservedQuantity != 0 || consumedQuantity != 0 || reservationId != null
                || consumptionOperationId != null || (movementIds != null && !movementIds.isEmpty());
    }
}
