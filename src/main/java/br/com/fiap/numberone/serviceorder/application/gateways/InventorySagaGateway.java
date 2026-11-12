package br.com.fiap.numberone.serviceorder.application.gateways;

import br.com.fiap.numberone.serviceorder.domain.entities.CompositionSnapshot.Material;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public interface InventorySagaGateway {
    record Consumption(UUID operationId, UUID serviceOrderId, UUID serviceOrderItemId,
                       UUID responsibleUserId, List<Material> items) { }
    record ConsumptionResult(UUID operationId, String result, List<UUID> movements, String code) { }
    record InventoryOperation(UUID operationId, UUID compensationId, String result, List<UUID> movements, String code) { }
    record Evidence(UUID verifiedBy, Instant verifiedAt, String reference) { }
    record Reversal(UUID compensationId, String reason, boolean unusedConfirmed, Evidence evidence) { }

    InventoryOperation inventoryOperation(UUID operationId, UUID correlationId);
    InventoryOperation reverse(UUID operationId, Reversal request, UUID correlationId);
    ConsumptionResult consume(Consumption request, UUID correlationId);
}
