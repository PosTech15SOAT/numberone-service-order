package br.com.fiap.numberone.serviceorder.infrastructure.persistence.gateways;

import br.com.fiap.numberone.serviceorder.application.gateways.ServiceOrderGateway;
import br.com.fiap.numberone.serviceorder.domain.entities.*;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderRuleException;
import br.com.fiap.numberone.serviceorder.infrastructure.persistence.entities.*;
import br.com.fiap.numberone.serviceorder.infrastructure.persistence.repositories.ServiceOrderRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Transactional
public class JpaServiceOrderGateway implements ServiceOrderGateway {
    private final ServiceOrderRepository repository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JpaServiceOrderGateway(ServiceOrderRepository repository, JdbcTemplate jdbc, ObjectMapper json) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.json = json;
    }

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
        boolean newComposition = entity.getCompositionVersion() != order.getCompositionVersion();
        entity.setId(order.getId());
        entity.setBranchId(order.getBranchId());
        entity.setCustomerId(order.getCustomerId());
        entity.setVehicleId(order.getVehicleId());
        entity.setAttemptId(order.getAttemptId());
        entity.setBudgetRevision(order.getBudgetRevision());
        entity.setCompositionVersion(order.getCompositionVersion());
        entity.setCurrentBudgetId(order.getCurrentBudgetId());
        entity.setBudgetCompositionVersion(order.getBudgetCompositionVersion());
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
        entity.setApprovedCompositionVersion(order.getApprovedComposition() == null ? null : order.getApprovedComposition().version());
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
        var saved = repository.saveAndFlush(entity);
        jdbc.update("INSERT INTO service_order_saga(service_order_id, state) VALUES (?, 'IN_DIAGNOSIS') ON CONFLICT DO NOTHING", saved.getId());
        if (newComposition) {
            var snapshot = order.compositionSnapshot();
            jdbc.update("""
                    INSERT INTO service_order_composition (service_order_id, composition_version, items)
                    VALUES (?, ?, CAST(? AS jsonb))
                    """, snapshot.serviceOrderId(), snapshot.version(), json.writeValueAsString(snapshot.items()));
        }
        var persisted = domain(saved);
        if (!Objects.equals(order.getApprovedComposition(), persisted.getApprovedComposition())) {
            throw new OrderRuleException("BUDGET_COMPOSITION_CONFLICT");
        }
        return persisted;
    }
    private void copyItem(ServiceOrderItem item, ServiceOrderItemEntity target) {
        target.setAutomotiveServiceId(item.getAutomotiveServiceId());
        target.setServiceDescription(item.getServiceDescription());
        target.setLaborUnitPrice(item.getLaborUnitPrice());
        target.setQuantity(item.getQuantity());
        target.setEstimatedTimeMinutes(item.getEstimatedTimeMinutes());
        target.setOptional(item.isOptional());
        target.setStatus(item.getStatus());
        target.setStartPending(item.isStartPending());
        target.setStartOperationId(item.getStartOperationId());
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
        var saga = jdbc.queryForMap("SELECT state, settlement_pending FROM service_order_saga WHERE service_order_id=?", entity.getId());
        return ServiceOrder.builder()
                .sagaState((String)saga.get("state"))
                .settlementPending((Boolean)saga.get("settlement_pending"))
                .payments(jdbc.query("SELECT payment_id,payment_version,status FROM service_order_payment WHERE service_order_id=? ORDER BY payment_id",
                        (r,n) -> new ServiceOrder.PaymentProjection(r.getObject(1, UUID.class), r.getInt(2), r.getString(3)), entity.getId()))
                .id(entity.getId())
                .version(entity.getVersion())
                .branchId(entity.getBranchId())
                .customerId(entity.getCustomerId())
                .vehicleId(entity.getVehicleId())
                .attemptId(entity.getAttemptId())
                .budgetRevision(entity.getBudgetRevision())
                .compositionVersion(entity.getCompositionVersion())
                .currentBudgetId(entity.getCurrentBudgetId())
                .budgetCompositionVersion(entity.getBudgetCompositionVersion())
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
                .approvedComposition(approvedComposition(entity))
                .paymentConfirmedAt(entity.getPaymentConfirmedAt())
                .items(entity.getItems().stream().map(this::domainItem).collect(Collectors.toCollection(ArrayList::new))).build();
    }

    private CompositionSnapshot approvedComposition(ServiceOrderEntity entity) {
        var version = entity.getApprovedCompositionVersion();
        if (version == null) { return null; }
        var items = jdbc.queryForObject("""
                SELECT items FROM service_order_composition WHERE service_order_id = ? AND composition_version = ?
                """, String.class, entity.getId(), version);
        return new CompositionSnapshot(entity.getId(), version, List.of(json.readValue(items, CompositionSnapshot.Item[].class)));
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
                .startPending(entity.isStartPending())
                .startOperationId(entity.getStartOperationId())
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
