package br.com.fiap.numberone.serviceorder.domain.valueobjects;

import br.com.fiap.numberone.serviceorder.domain.entities.MaterialAllocation;
import br.com.fiap.numberone.serviceorder.domain.entities.ServiceOrder;
import br.com.fiap.numberone.serviceorder.domain.entities.ServiceOrderItem;
import br.com.fiap.numberone.serviceorder.domain.enums.OrderItemStatus;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record MaintenanceSnapshot(UUID serviceOrderId, UUID vehicleId, UUID branchId, String completedAt,
                                  int historyVersion, Integer odometerKm, List<ExecutedService> services) {
    private static final DateTimeFormatter KEY_TIME = new DateTimeFormatterBuilder().appendInstant(6).toFormatter();

    public record ExecutedService(UUID serviceOrderItemId, UUID serviceId, String description, int quantity,
                                  List<ConsumedMaterial> materials) { }

    public record ConsumedMaterial(UUID allocationId, UUID materialId, String description, int quantity,
                                   UUID consumptionOperationId, List<UUID> movementIds) { }

    public static MaintenanceSnapshot capture(ServiceOrder order, Instant completedAt, Integer odometerKm) {
        var services = order.getItems().stream()
                .filter(item -> item.getStatus() == OrderItemStatus.COMPLETED)
                .sorted(Comparator.comparing(ServiceOrderItem::getId))
                .map(MaintenanceSnapshot::captureService)
                .toList();
        return new MaintenanceSnapshot(order.getId(), order.getVehicleId(), order.getBranchId(),
                KEY_TIME.format(completedAt), 1, odometerKm, services);
    }

    public String historyKey() {
        return completedAt + "#" + serviceOrderId + "#" + historyVersion;
    }

    private static ExecutedService captureService(ServiceOrderItem item) {
        var materials = item.getMaterials().stream()
                .filter(MaterialAllocation::hasConfirmedConsumption)
                .sorted(Comparator.comparing(MaterialAllocation::getId))
                .map(material -> new ConsumedMaterial(material.getId(), material.getMaterialId(),
                        material.getDescription(), material.getConsumedQuantity(), material.getConsumptionOperationId(),
                        material.getMovementIds().stream().sorted().toList()))
                .toList();
        return new ExecutedService(item.getId(), item.getAutomotiveServiceId(), item.getServiceDescription(),
                item.getQuantity(), materials);
    }
}
