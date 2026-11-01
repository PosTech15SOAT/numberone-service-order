package br.com.fiap.numberone.serviceorder.domain.entities;

import br.com.fiap.numberone.serviceorder.domain.enums.OrderItemStatus;
import br.com.fiap.numberone.serviceorder.domain.enums.ServiceOrderStatus;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderNotFoundException;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderRuleException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local aggregate. No budget calculation, inventory balance, payment or messaging logic. */
@Getter @Builder @AllArgsConstructor
public class ServiceOrder {
    private UUID id;
    private Long version;
    private UUID branchId;
    private UUID customerId;
    private UUID vehicleId;
    private UUID attemptId;
    @Builder.Default private int budgetRevision = 1;
    private String initialDescription;
    private String diagnosisDescription;
    private String finalDiagnosisDescription;
    private String notes;
    @Builder.Default private ServiceOrderStatus status = ServiceOrderStatus.RECEIVED;
    private LocalDateTime entryDateTime;
    private LocalDateTime expectedDateTime;
    private LocalDateTime deliveryDateTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // Read-only projection to be populated by validated results in later phases, never by HTTP commands.
    private UUID approvedBudgetId;
    private Integer approvedRevision;
    private String approvedCurrency;
    private BigDecimal approvedAmount;
    private String approvedItemsJson;
    private LocalDateTime paymentConfirmedAt;
    @Builder.Default private List<ServiceOrderItem> items = new ArrayList<>();

    public void diagnose(String finalDescription, LocalDateTime expected, String notes) {
        if (status != ServiceOrderStatus.RECEIVED && status != ServiceOrderStatus.IN_DIAGNOSIS) {
            throw new OrderRuleException("DIAGNOSIS_NOT_EDITABLE");
        }
        requireNoApprovedSnapshot();
        if (finalDescription == null || finalDescription.isBlank() || expected == null || expected.isBefore(entryDateTime)) {
            throw new OrderRuleException("INVALID_DIAGNOSIS");
        }
        finalDiagnosisDescription = finalDescription;
        expectedDateTime = expected;
        this.notes = notes;
        status = ServiceOrderStatus.IN_DIAGNOSIS;
    }

    public void requireEditableDiagnosis() {
        if (status != ServiceOrderStatus.IN_DIAGNOSIS) { throw new OrderRuleException("DIAGNOSIS_NOT_EDITABLE"); }
        requireNoApprovedSnapshot();
    }

    private void requireNoApprovedSnapshot() {
        if (approvedBudgetId != null || approvedItemsJson != null) {
            throw new OrderRuleException("APPROVED_SNAPSHOT_IMMUTABLE");
        }
    }

    public ServiceOrderItem item(UUID itemId) {
        return items.stream().filter(i -> i.getId().equals(itemId)).findFirst().orElseThrow(OrderNotFoundException::new);
    }

    public void addItem(ServiceOrderItem item) {
        requireEditableDiagnosis();
        items.add(item);
    }

    public void removeItem(UUID itemId) {
        requireEditableDiagnosis();
        var item = item(itemId);
        item.requireUnallocatedMaterials();
        if (item.getStatus() != OrderItemStatus.PENDING) { throw new OrderRuleException("ITEM_NOT_EDITABLE"); }
        items.remove(item);
    }

    public void putMaterial(UUID itemId, MaterialAllocation material, boolean update) {
        requireEditableDiagnosis();
        var item = item(itemId);
        item.requireUnallocatedMaterials();
        if (item.getStatus() != OrderItemStatus.PENDING) { throw new OrderRuleException("ITEM_NOT_EDITABLE"); }
        if (material.getPlannedQuantity() <= 0 || material.getReferenceUnitPrice().signum() < 0) {
            throw new OrderRuleException("INVALID_MATERIAL_QUANTITY_OR_PRICE");
        }
        if (update && item.getMaterials().stream().noneMatch(m -> m.getId().equals(material.getId()))) {
            throw new OrderNotFoundException();
        }
        if (item.getMaterials().stream().anyMatch(m -> m.getMaterialId().equals(material.getMaterialId())
                && !m.getId().equals(material.getId()))) { throw new OrderRuleException("DUPLICATE_MATERIAL"); }
        item.getMaterials().removeIf(m -> m.getId().equals(material.getId()));
        item.getMaterials().add(material);
    }

    public void removeMaterial(UUID itemId, UUID materialAllocationId) {
        requireEditableDiagnosis();
        var item = item(itemId);
        item.requireUnallocatedMaterials();
        if (item.getStatus() != OrderItemStatus.PENDING) { throw new OrderRuleException("ITEM_NOT_EDITABLE"); }
        if (!item.getMaterials().removeIf(m -> m.getId().equals(materialAllocationId))) { throw new OrderNotFoundException(); }
    }

    public void requireConfirmedPayment() {
        if (approvedBudgetId == null || approvedRevision == null || approvedRevision != budgetRevision
                || approvedAmount == null || approvedCurrency == null || approvedItemsJson == null
                || paymentConfirmedAt == null) { throw new OrderRuleException("PAYMENT_NOT_CONFIRMED"); }
    }

    public void requestStart() {
        requireConfirmedPayment();
        if (status != ServiceOrderStatus.READY_FOR_EXECUTION && status != ServiceOrderStatus.IN_PROGRESS) {
            throw new OrderRuleException("ORDER_NOT_READY_FOR_EXECUTION");
        }
        // Even a preloaded fixture cannot bypass the missing phase-4 integration.
        throw new OrderRuleException("EXECUTION_INTEGRATION_NOT_AVAILABLE");
    }

    public void completeItem(UUID itemId, LocalDateTime now) {
        requireConfirmedPayment();
        if (status != ServiceOrderStatus.IN_PROGRESS) { throw new OrderRuleException("ORDER_NOT_IN_PROGRESS"); }
        item(itemId).complete(now);
    }

    public void complete() {
        requireConfirmedPayment();
        if (status != ServiceOrderStatus.IN_PROGRESS) { throw new OrderRuleException("ORDER_NOT_IN_PROGRESS"); }
        var eligible = items.stream().filter(i -> i.getStatus() != OrderItemStatus.CANCELLED).toList();
        if (eligible.isEmpty() || eligible.stream().anyMatch(i -> i.getStatus() != OrderItemStatus.COMPLETED
                || i.getStartDateTime() == null || i.getEndDateTime() == null
                || !i.getMaterials().stream().allMatch(MaterialAllocation::hasConfirmedConsumption))) {
            throw new OrderRuleException("ORDER_HAS_UNFINISHED_ITEMS");
        }
        if (items.stream().filter(i -> i.getStatus() == OrderItemStatus.CANCELLED)
                .flatMap(i -> i.getMaterials().stream()).anyMatch(m -> m.getReservedQuantity() != 0 || m.getConsumedQuantity() != 0)) {
            throw new OrderRuleException("MATERIAL_COMPENSATION_PENDING");
        }
        status = ServiceOrderStatus.COMPLETED;
    }

    public void deliver(LocalDateTime now) {
        if (status != ServiceOrderStatus.COMPLETED) { throw new OrderRuleException("ORDER_NOT_COMPLETED"); }
        status = ServiceOrderStatus.DELIVERED;
        deliveryDateTime = now;
    }

    public void cancel() {
        if (status != ServiceOrderStatus.RECEIVED && status != ServiceOrderStatus.IN_DIAGNOSIS) {
            throw new OrderRuleException("CANCELLATION_REQUIRES_RECONCILIATION");
        }
        requireNoApprovedSnapshot();
        if (items.stream().flatMap(i -> i.getMaterials().stream())
                .anyMatch(m -> m.getReservedQuantity() != 0 || m.getConsumedQuantity() != 0)) {
            throw new OrderRuleException("CANCELLATION_REQUIRES_RECONCILIATION");
        }
        items.stream().filter(i -> i.getStatus() != OrderItemStatus.CANCELLED).forEach(ServiceOrderItem::cancelBeforeApproval);
        status = ServiceOrderStatus.CANCELLED;
    }

    public BigDecimal estimate() {
        return items.stream().filter(i -> i.getStatus() != OrderItemStatus.CANCELLED)
                .map(ServiceOrderItem::estimate).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public long estimatedMinutes() {
        return items.stream().filter(i -> i.getStatus() != OrderItemStatus.CANCELLED)
                .mapToLong(i -> (long) i.getEstimatedTimeMinutes() * i.getQuantity()).sum();
    }

    public long countItems(OrderItemStatus state) { return items.stream().filter(i -> i.getStatus() == state).count(); }

    public long averageExecutionMinutes() {
        return (long) items.stream().filter(i -> i.getStatus() == OrderItemStatus.COMPLETED)
                .filter(i -> i.getStartDateTime() != null && i.getEndDateTime() != null)
                .mapToLong(i -> java.time.Duration.between(i.getStartDateTime(), i.getEndDateTime()).toMinutes())
                .average().orElse(0);
    }
}
