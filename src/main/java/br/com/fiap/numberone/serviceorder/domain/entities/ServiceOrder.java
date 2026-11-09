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
import java.util.Comparator;
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
    @Builder.Default private int compositionVersion = 1;
    private UUID currentBudgetId;
    private Integer budgetCompositionVersion;
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
    // Commercial approval and technical snapshot linked by a validated Billing result.
    private UUID approvedBudgetId;
    private Integer approvedRevision;
    private String approvedCurrency;
    private BigDecimal approvedAmount;
    private String approvedItemsJson;
    private CompositionSnapshot approvedComposition;
    private LocalDateTime paymentConfirmedAt;
    private String sagaState;
    private boolean settlementPending;
    @Builder.Default private List<PaymentProjection> payments = List.of();
    public record PaymentProjection(UUID paymentId, int paymentVersion, String paymentStatus) { }
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
        compositionVersion++;
    }

    public void requireEditableDiagnosis() {
        if (status != ServiceOrderStatus.IN_DIAGNOSIS) { throw new OrderRuleException("DIAGNOSIS_NOT_EDITABLE"); }
        requireNoApprovedSnapshot();
    }

    private void requireNoApprovedSnapshot() {
        if (approvedBudgetId != null || approvedItemsJson != null || approvedComposition != null) {
            throw new OrderRuleException("APPROVED_SNAPSHOT_IMMUTABLE");
        }
    }

    public ServiceOrderItem item(UUID itemId) {
        return items.stream().filter(i -> i.getId().equals(itemId)).findFirst().orElseThrow(OrderNotFoundException::new);
    }

    public void addItem(ServiceOrderItem item) {
        requireEditableDiagnosis();
        items.add(item);
        compositionVersion++;
    }

    public void removeItem(UUID itemId) {
        requireEditableDiagnosis();
        var item = item(itemId);
        item.requireUnallocatedMaterials();
        requireEditableItem(item);
        items.remove(item);
        compositionVersion++;
    }

    public void cancelItem(UUID itemId) {
        requireEditableDiagnosis();
        item(itemId).cancelBeforeApproval();
        compositionVersion++;
    }

    public void putMaterial(UUID itemId, MaterialAllocation material, boolean update) {
        requireEditableDiagnosis();
        var item = item(itemId);
        item.requireUnallocatedMaterials();
        requireEditableItem(item);
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
        compositionVersion++;
    }

    public void removeMaterial(UUID itemId, UUID materialAllocationId) {
        requireEditableDiagnosis();
        var item = item(itemId);
        item.requireUnallocatedMaterials();
        requireEditableItem(item);
        if (!item.getMaterials().removeIf(m -> m.getId().equals(materialAllocationId))) { throw new OrderNotFoundException(); }
        compositionVersion++;
    }

    private void requireEditableItem(ServiceOrderItem item) {
        if (item.getStatus() != OrderItemStatus.PENDING || item.isStartPending()) {
            throw new OrderRuleException("ITEM_NOT_EDITABLE");
        }
    }

    public CompositionSnapshot compositionSnapshot() {
        var composition = items.stream().filter(i -> i.getStatus() != OrderItemStatus.CANCELLED)
                .sorted(Comparator.comparing(i -> i.getId().toString()))
                .map(i -> new CompositionSnapshot.Item(i.getId(), i.getAutomotiveServiceId(), i.getQuantity(), i.isOptional(),
                        i.getLaborUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())).setScale(2).toPlainString(),
                        i.getMaterials().stream().sorted(Comparator.comparing(m -> m.getMaterialId().toString()))
                                .map(m -> new CompositionSnapshot.Material(m.getMaterialId(), m.getPlannedQuantity())).toList()))
                .toList();
        return new CompositionSnapshot(id, compositionVersion, composition);
    }

    public void registerBudget(UUID budgetId, int revision, int quotedCompositionVersion) {
        requireNoApprovedSnapshot();
        if (revision < budgetRevision) {
            throw new OrderRuleException("STALE_BUDGET_REVISION");
        }
        if (revision == budgetRevision && currentBudgetId != null
                && (!currentBudgetId.equals(budgetId) || !Integer.valueOf(quotedCompositionVersion).equals(budgetCompositionVersion))) {
            throw new OrderRuleException("BUDGET_COMPOSITION_CONFLICT");
        }
        if (quotedCompositionVersion < compositionVersion) {
            throw new OrderRuleException("STALE_BUDGET_REVISION");
        }
        if (budgetId == null || quotedCompositionVersion != compositionVersion) {
            throw new OrderRuleException("BUDGET_COMPOSITION_CONFLICT");
        }
        if (status != ServiceOrderStatus.IN_DIAGNOSIS && status != ServiceOrderStatus.WAITING_APPROVAL
                && status != ServiceOrderStatus.REJECTED) {
            throw new OrderRuleException("ORDER_NOT_WAITING_FOR_APPROVAL");
        }
        if (status == ServiceOrderStatus.REJECTED && revision == budgetRevision) {
            throw new OrderRuleException("STALE_BUDGET_REVISION");
        }
        currentBudgetId = budgetId;
        budgetRevision = revision;
        budgetCompositionVersion = quotedCompositionVersion;
        status = ServiceOrderStatus.WAITING_APPROVAL;
    }

    public void approveBudget(UUID budgetId, int revision, CompositionSnapshot quoted, BigDecimal amount, String currency) {
        requireNoApprovedSnapshot();
        if (revision < budgetRevision) {
            throw new OrderRuleException("STALE_BUDGET_REVISION");
        }
        if (status != ServiceOrderStatus.WAITING_APPROVAL || currentBudgetId == null || revision > budgetRevision) {
            throw new OrderRuleException("BUDGET_CONTEXT_UNAVAILABLE");
        }
        if (!currentBudgetId.equals(budgetId) || quoted == null || budgetCompositionVersion == null
                || quoted.version() != budgetCompositionVersion || !quoted.equals(compositionSnapshot())) {
            throw new OrderRuleException("BUDGET_COMPOSITION_CONFLICT");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() != 2 || amount.precision() > 19
                || !"BRL".equals(currency) || quoted.items().isEmpty()) {
            throw new OrderRuleException("INVALID_APPROVED_SNAPSHOT");
        }
        approvedBudgetId = budgetId;
        approvedRevision = revision;
        approvedAmount = amount;
        approvedCurrency = currency;
        approvedComposition = quoted;
        status = ServiceOrderStatus.APPROVED;
    }

    public boolean hasCurrentApproval() {
        return hasApprovedBudget() && approvedBudgetId.equals(currentBudgetId) && approvedComposition != null
                && approvedComposition.version() == compositionVersion
                && Integer.valueOf(compositionVersion).equals(budgetCompositionVersion)
                && approvedComposition.equals(compositionSnapshot());
    }

    public boolean hasApprovedBudget() {
        return approvedBudgetId != null && approvedRevision != null && approvedRevision == budgetRevision
                && approvedAmount != null && approvedCurrency != null
                && (approvedComposition != null || approvedItemsJson != null);
    }

    public void requestStart() {
        requireStartable();
    }

    public void rejectBudget() {
        if (status != ServiceOrderStatus.WAITING_APPROVAL) { throw new OrderRuleException("BUDGET_DECISION_CONFLICT"); }
        status = ServiceOrderStatus.REJECTED;
    }

    public void projectPayment(LocalDateTime confirmedAt) { paymentConfirmedAt = confirmedAt; }

    public void confirmItemStart(UUID itemId, LocalDateTime now) {
        requireStartable();
        item(itemId).confirmStart(now);
        status = ServiceOrderStatus.IN_PROGRESS;
    }

    private void requireStartable() {
        if (!hasCurrentApproval()) { throw new OrderRuleException("BUDGET_NOT_APPROVED"); }
        if (status != ServiceOrderStatus.APPROVED && status != ServiceOrderStatus.IN_PROGRESS) {
            throw new OrderRuleException("ORDER_NOT_READY_FOR_EXECUTION");
        }
    }

    public void completeItem(UUID itemId, LocalDateTime now) {
        requireInProgress();
        item(itemId).complete(now);
    }

    public void complete() {
        requireInProgress();
        requireTechnicalCompletion();
        status = ServiceOrderStatus.COMPLETED;
    }

    private void requireTechnicalCompletion() {
        var eligible = items.stream().filter(i -> i.getStatus() != OrderItemStatus.CANCELLED).toList();
        if (eligible.isEmpty() || eligible.stream().anyMatch(i -> i.getStatus() != OrderItemStatus.COMPLETED
                || i.isStartPending()
                || i.getStartDateTime() == null || i.getEndDateTime() == null
                || i.getEndDateTime().isBefore(i.getStartDateTime())
                || !i.getMaterials().stream().allMatch(MaterialAllocation::hasConfirmedConsumption))) {
            throw new OrderRuleException("ORDER_HAS_UNFINISHED_ITEMS");
        }
        if (items.stream().filter(i -> i.getStatus() == OrderItemStatus.CANCELLED)
                .flatMap(i -> i.getMaterials().stream()).anyMatch(MaterialAllocation::hasMaterialEffect)) {
            throw new OrderRuleException("MATERIAL_COMPENSATION_PENDING");
        }
    }

    private void requireInProgress() {
        if (status != ServiceOrderStatus.IN_PROGRESS) { throw new OrderRuleException("ORDER_NOT_IN_PROGRESS"); }
        if (approvedComposition == null ? !hasApprovedBudget() : !hasCurrentApproval()) {
            throw new OrderRuleException("BUDGET_NOT_APPROVED");
        }
    }

    public void requestDelivery() {
        if (status != ServiceOrderStatus.COMPLETED) { throw new OrderRuleException("ORDER_NOT_COMPLETED"); }
    }

    public void confirmDelivery(LocalDateTime now) {
        requestDelivery();
        requireTechnicalCompletion();
        if (!hasCurrentApproval() || items.stream().anyMatch(i -> i.isStartPending()
                || (i.getStatus() != OrderItemStatus.COMPLETED && i.getStatus() != OrderItemStatus.CANCELLED))) {
            throw new OrderRuleException("ORDER_HAS_UNFINISHED_ITEMS");
        }
        status = ServiceOrderStatus.DELIVERED;
        deliveryDateTime = now;
    }

    public void cancel() {
        if (status != ServiceOrderStatus.RECEIVED && status != ServiceOrderStatus.IN_DIAGNOSIS) {
            throw new OrderRuleException("CANCELLATION_REQUIRES_RECONCILIATION");
        }
        requireNoApprovedSnapshot();
        if (items.stream().flatMap(i -> i.getMaterials().stream())
                .anyMatch(MaterialAllocation::hasMaterialEffect)) {
            throw new OrderRuleException("CANCELLATION_REQUIRES_RECONCILIATION");
        }
        items.stream().filter(i -> i.getStatus() != OrderItemStatus.CANCELLED).forEach(ServiceOrderItem::cancelBeforeApproval);
        compositionVersion++;
        status = ServiceOrderStatus.CANCELLED;
    }

    /** Only called by C6 after durable confirmation of all remote effects. */
    public void confirmCompensatedCancellation() {
        if (!java.util.Set.of(ServiceOrderStatus.RECEIVED, ServiceOrderStatus.IN_DIAGNOSIS,
                ServiceOrderStatus.WAITING_APPROVAL, ServiceOrderStatus.APPROVED, ServiceOrderStatus.REJECTED).contains(status)
                || items.stream().anyMatch(i -> i.getStartDateTime()!=null)) {
            throw new OrderRuleException("CANCELLATION_REQUIRES_RECONCILIATION");
        }
        items.forEach(ServiceOrderItem::confirmCompensatedCancellation);
        status=ServiceOrderStatus.CANCELLED;
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
