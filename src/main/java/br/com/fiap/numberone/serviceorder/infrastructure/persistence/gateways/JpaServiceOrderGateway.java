package br.com.fiap.numberone.serviceorder.infrastructure.persistence.gateways;

import br.com.fiap.numberone.serviceorder.application.gateways.ServiceOrderGateway;
import br.com.fiap.numberone.serviceorder.domain.entities.*;
import br.com.fiap.numberone.serviceorder.infrastructure.persistence.entities.*;
import br.com.fiap.numberone.serviceorder.infrastructure.persistence.repositories.ServiceOrderRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Transactional
public class JpaServiceOrderGateway implements ServiceOrderGateway {
    private final ServiceOrderRepository repository;
    public JpaServiceOrderGateway(ServiceOrderRepository repository) { this.repository = repository; }

    public Optional<ServiceOrder> find(UUID id, UUID branchId, boolean forUpdate) {
        return (forUpdate ? repository.lockById(id, branchId) : repository.findByIdAndBranchId(id, branchId)).map(this::domain);
    }
    public Optional<ServiceOrder> findByItem(UUID itemId, UUID branchId, boolean forUpdate) {
        return repository.orderIdForItem(itemId, branchId).flatMap(id -> find(id, branchId, forUpdate));
    }
    public List<ServiceOrder> findAll(UUID branchId) {
        return repository.findByBranchIdOrderByCreatedAtDesc(branchId).stream().map(this::domain).toList();
    }
    public ServiceOrder save(ServiceOrder order) {
        var entity = repository.findById(order.getId()).orElseGet(ServiceOrderEntity::new);
        if (!Objects.equals(entity.getVersion(), order.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(ServiceOrderEntity.class, order.getId());
        }
        entity.setId(order.getId());
        entity.setBranchId(order.getBranchId());
        entity.setCustomerId(order.getCustomerId());
        entity.setVehicleId(order.getVehicleId());
        entity.setAttemptId(order.getAttemptId());
        entity.setBudgetRevision(order.getBudgetRevision());
        entity.setInitialDescription(order.getInitialDescription());
        entity.setDiagnosisDescription(order.getDiagnosisDescription());
        entity.setFinalDiagnosisDescription(order.getFinalDiagnosisDescription());
        entity.setNotes(order.getNotes());
        entity.setStatus(order.getStatus());
        entity.setEntryDateTime(order.getEntryDateTime());
        entity.setExpectedDateTime(order.getExpectedDateTime());
        entity.setDeliveryDateTime(order.getDeliveryDateTime());
        entity.setCreatedAt(order.getCreatedAt());
        entity.setUpdatedAt(order.getUpdatedAt());
        entity.setApprovedBudgetId(order.getApprovedBudgetId());
        entity.setApprovedRevision(order.getApprovedRevision());
        entity.setApprovedCurrency(order.getApprovedCurrency());
        entity.setApprovedAmount(order.getApprovedAmount());
        entity.setApprovedItemsJson(order.getApprovedItemsJson());
        entity.setPaymentConfirmedAt(order.getPaymentConfirmedAt());
        var now = LocalDateTime.now(ZoneOffset.UTC);
        if (entity.getCreatedAt() == null) { entity.setCreatedAt(now); }
        entity.setUpdatedAt(now);
        var existing = entity.getItems().stream().collect(Collectors.toMap(ServiceOrderItemEntity::getId, i -> i));
        var kept = new HashSet<UUID>();
        for (var item : order.getItems()) {
            var target = existing.get(item.getId());
            if (target == null) {
                target = new ServiceOrderItemEntity();
                target.setId(item.getId());
                target.setOrder(entity);
                entity.getItems().add(target);
            }
            copyItem(item, target);
            kept.add(item.getId());
        }
        entity.getItems().removeIf(i -> !kept.contains(i.getId()));
        return domain(repository.saveAndFlush(entity));
    }
    private void copyItem(ServiceOrderItem item, ServiceOrderItemEntity target) {
        target.setAutomotiveServiceId(item.getAutomotiveServiceId());
        target.setServiceDescription(item.getServiceDescription());
        target.setLaborUnitPrice(item.getLaborUnitPrice());
        target.setQuantity(item.getQuantity());
        target.setEstimatedTimeMinutes(item.getEstimatedTimeMinutes());
        target.setOptional(item.isOptional());
        target.setStatus(item.getStatus());
        target.setStartDateTime(item.getStartDateTime());
        target.setEndDateTime(item.getEndDateTime());
        var existing = target.getMaterials().stream().collect(Collectors.toMap(MaterialAllocationEntity::getId, m -> m));
        var kept = new HashSet<UUID>();
        for (var material : item.getMaterials()) {
            var saved = existing.get(material.getId());
            if (saved == null) {
                saved = new MaterialAllocationEntity();
                saved.setId(material.getId());
                saved.setItem(target);
                target.getMaterials().add(saved);
            }
            copyMaterial(material, saved);
            kept.add(material.getId());
        }
        target.getMaterials().removeIf(m -> !kept.contains(m.getId()));
    }
    private void copyMaterial(MaterialAllocation material, MaterialAllocationEntity saved) {
        saved.setMaterialId(material.getMaterialId());
        saved.setDescription(material.getDescription());
        saved.setReferenceUnitPrice(material.getReferenceUnitPrice());
        saved.setPlannedQuantity(material.getPlannedQuantity());
        saved.setReservedQuantity(material.getReservedQuantity());
        saved.setConsumedQuantity(material.getConsumedQuantity());
        saved.setReservationId(material.getReservationId());
        saved.setConsumptionOperationId(material.getConsumptionOperationId());
        if (!saved.getMovementIds().equals(material.getMovementIds())) {
            saved.getMovementIds().clear();
            saved.getMovementIds().addAll(material.getMovementIds());
        }
    }
    private ServiceOrder domain(ServiceOrderEntity entity) {
        return ServiceOrder.builder()
                .id(entity.getId())
                .version(entity.getVersion())
                .branchId(entity.getBranchId())
                .customerId(entity.getCustomerId())
                .vehicleId(entity.getVehicleId())
                .attemptId(entity.getAttemptId())
                .budgetRevision(entity.getBudgetRevision())
                .initialDescription(entity.getInitialDescription())
                .diagnosisDescription(entity.getDiagnosisDescription())
                .finalDiagnosisDescription(entity.getFinalDiagnosisDescription())
                .notes(entity.getNotes())
                .status(entity.getStatus())
                .entryDateTime(entity.getEntryDateTime())
                .expectedDateTime(entity.getExpectedDateTime())
                .deliveryDateTime(entity.getDeliveryDateTime())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .approvedBudgetId(entity.getApprovedBudgetId())
                .approvedRevision(entity.getApprovedRevision())
                .approvedCurrency(entity.getApprovedCurrency())
                .approvedAmount(entity.getApprovedAmount())
                .approvedItemsJson(entity.getApprovedItemsJson())
                .paymentConfirmedAt(entity.getPaymentConfirmedAt())
                .items(entity.getItems().stream().map(this::domainItem).collect(Collectors.toCollection(ArrayList::new))).build();
    }
    private ServiceOrderItem domainItem(ServiceOrderItemEntity entity) {
        return ServiceOrderItem.builder()
                .id(entity.getId())
                .automotiveServiceId(entity.getAutomotiveServiceId())
                .serviceDescription(entity.getServiceDescription())
                .laborUnitPrice(entity.getLaborUnitPrice())
                .quantity(entity.getQuantity())
                .estimatedTimeMinutes(entity.getEstimatedTimeMinutes())
                .optional(entity.isOptional())
                .status(entity.getStatus())
                .startDateTime(entity.getStartDateTime())
                .endDateTime(entity.getEndDateTime())
                .materials(entity.getMaterials().stream().map(this::domainMaterial).collect(Collectors.toCollection(ArrayList::new))).build();
    }
    private MaterialAllocation domainMaterial(MaterialAllocationEntity entity) {
        return MaterialAllocation.builder()
                .id(entity.getId())
                .materialId(entity.getMaterialId())
                .description(entity.getDescription())
                .referenceUnitPrice(entity.getReferenceUnitPrice())
                .plannedQuantity(entity.getPlannedQuantity())
                .reservedQuantity(entity.getReservedQuantity())
                .consumedQuantity(entity.getConsumedQuantity())
                .reservationId(entity.getReservationId())
                .consumptionOperationId(entity.getConsumptionOperationId())
                .movementIds(List.copyOf(entity.getMovementIds())).build();
    }
}
