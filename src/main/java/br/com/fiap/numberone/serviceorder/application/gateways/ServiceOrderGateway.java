package br.com.fiap.numberone.serviceorder.application.gateways;

import br.com.fiap.numberone.serviceorder.domain.entities.ServiceOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderGateway {
    Optional<ServiceOrder> find(UUID id, UUID branchId, boolean forUpdate);
    Optional<ServiceOrder> findByItem(UUID itemId, UUID branchId, boolean forUpdate);
    List<ServiceOrder> findAll(UUID branchId);
    ServiceOrder save(ServiceOrder order);
}
