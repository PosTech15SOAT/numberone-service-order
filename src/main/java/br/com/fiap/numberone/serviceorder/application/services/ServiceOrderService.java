package br.com.fiap.numberone.serviceorder.application.services;

import br.com.fiap.numberone.automotiveservice.application.services.AutomotiveServiceService;
import br.com.fiap.numberone.customer.application.services.CustomerService;
import br.com.fiap.numberone.vehicle.application.services.VehicleService;
import br.com.fiap.numberone.serviceorder.application.gateways.*;
import br.com.fiap.numberone.serviceorder.domain.entities.*;
import br.com.fiap.numberone.serviceorder.domain.exceptions.*;
import br.com.fiap.numberone.shared.application.gateways.BranchScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ServiceOrderService {
    private final ServiceOrderGateway orders;
    private final CustomerService customers;
    private final VehicleService vehicles;
    private final AutomotiveServiceService services;
    private final MaterialReferenceGateway materials;
    private final BranchScope branch;

    public ServiceOrderService(ServiceOrderGateway orders, CustomerService customers, VehicleService vehicles,
            AutomotiveServiceService services, MaterialReferenceGateway materials, BranchScope branch) {
        this.orders = orders; this.customers = customers; this.vehicles = vehicles;
        this.services = services; this.materials = materials; this.branch = branch;
    }

    public ServiceOrder create(UUID customerId, UUID vehicleId, String description,
            String diagnosis, String notes, LocalDateTime entry) {
        var customer = customers.findById(customerId);
        if (!Boolean.TRUE.equals(customer.getActive())) { throw new OrderRuleException("CUSTOMER_NOT_ACTIVE"); }
        var vehicle = vehicles.findById(vehicleId);
        if (!customerId.equals(vehicle.getCustomerId())) { throw new OrderRuleException("VEHICLE_CUSTOMER_MISMATCH"); }
        return orders.save(ServiceOrder.builder().id(UUID.randomUUID()).branchId(branch.id())
                .customerId(customerId).vehicleId(vehicleId).attemptId(UUID.randomUUID())
                .initialDescription(description).diagnosisDescription(diagnosis).notes(notes).entryDateTime(entry).build());
    }

    @Transactional(readOnly = true)
    public ServiceOrder get(UUID id) { return load(id, false); }

    @Transactional(readOnly = true)
    public List<ServiceOrder> list() { return orders.findAll(branch.id()); }

    public ServiceOrder diagnose(UUID id, String description, LocalDateTime expected, String notes) {
        var order = load(id, true);
        order.diagnose(description, expected, notes);
        return orders.save(order);
    }

    public ServiceOrderItem addItem(UUID orderId, UUID serviceId, BigDecimal estimate, int quantity, boolean optional) {
        var order = load(orderId, true);
        order.requireEditableDiagnosis();
        var service = services.findById(serviceId);
        if (!Boolean.TRUE.equals(service.getActive())) { throw new OrderRuleException("AUTOMOTIVE_SERVICE_NOT_ACTIVE"); }
        if (quantity <= 0 || estimate == null || estimate.signum() < 0 || estimate.scale() > 2) {
            throw new OrderRuleException("INVALID_SERVICE_ESTIMATE");
        }
        var item = ServiceOrderItem.builder().id(UUID.randomUUID()).automotiveServiceId(serviceId)
                .serviceDescription(service.getName()).laborUnitPrice(estimate).quantity(quantity)
                .estimatedTimeMinutes(service.getEstimatedTimeMinutes()).optional(optional).build();
        order.addItem(item);
        return orders.save(order).item(item.getId());
    }

    public void removeItem(UUID itemId) {
        var order = byItem(itemId, true);
        order.removeItem(itemId);
        orders.save(order);
    }

    public ServiceOrderItem cancelItem(UUID itemId) {
        var order = byItem(itemId, true);
        order.requireEditableDiagnosis();
        order.item(itemId).cancelBeforeApproval();
        return orders.save(order).item(itemId);
    }

    public ServiceOrderItem completeItem(UUID itemId) {
        var order = byItem(itemId, true);
        order.completeItem(itemId, now());
        return orders.save(order).item(itemId);
    }

    public MaterialAllocation putMaterial(UUID itemId, UUID allocationId, UUID materialId, int quantity) {
        var order = byItem(itemId, true);
        order.requireEditableDiagnosis();
        var reference = materials.find(materialId);
        if (!materialId.equals(reference.id()) || !reference.active()) { throw new OrderRuleException("MATERIAL_NOT_ACTIVE"); }
        UUID id = allocationId == null ? UUID.randomUUID() : allocationId;
        var allocation = MaterialAllocation.builder().id(id).materialId(materialId)
                .description(reference.description()).referenceUnitPrice(reference.referenceUnitPrice())
                .plannedQuantity(quantity).build();
        order.putMaterial(itemId, allocation, allocationId != null);
        return orders.save(order).item(itemId).getMaterials().stream().filter(m -> m.getId().equals(id)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<MaterialAllocation> listMaterials(UUID itemId) { return byItem(itemId, false).item(itemId).getMaterials(); }

    public void removeMaterial(UUID itemId, UUID allocationId) {
        var order = byItem(itemId, true);
        order.removeMaterial(itemId, allocationId);
        orders.save(order);
    }

    public void requestStart(UUID orderId) { load(orderId, true).requestStart(); }
    public void requestItemStart(UUID itemId) { byItem(itemId, true).requestStart(); }

    public ServiceOrder cancel(UUID id) { var o = load(id, true); o.cancel(); return orders.save(o); }
    public ServiceOrder complete(UUID id) { var o = load(id, true); o.complete(); return orders.save(o); }
    public ServiceOrder deliver(UUID id) { var o = load(id, true); o.deliver(now()); return orders.save(o); }

    private ServiceOrder load(UUID id, boolean forUpdate) {
        return orders.find(id, branch.id(), forUpdate).orElseThrow(OrderNotFoundException::new);
    }
    private ServiceOrder byItem(UUID id, boolean forUpdate) {
        return orders.findByItem(id, branch.id(), forUpdate).orElseThrow(OrderNotFoundException::new);
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
}
