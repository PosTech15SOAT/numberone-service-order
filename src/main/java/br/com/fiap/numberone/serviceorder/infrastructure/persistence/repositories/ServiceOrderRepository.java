package br.com.fiap.numberone.serviceorder.infrastructure.persistence.repositories;

import br.com.fiap.numberone.serviceorder.infrastructure.persistence.entities.ServiceOrderEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrderEntity, UUID> {
    Optional<ServiceOrderEntity> findByIdAndBranchId(UUID id, UUID branchId);
    List<ServiceOrderEntity> findByBranchIdOrderByCreatedAtDesc(UUID branchId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ServiceOrderEntity o where o.id = :id and o.branchId = :branchId")
    Optional<ServiceOrderEntity> lockById(UUID id, UUID branchId);
    @Query("select i.order.id from ServiceOrderItemEntity i where i.id = :itemId and i.order.branchId = :branchId")
    Optional<UUID> orderIdForItem(UUID itemId, UUID branchId);
}
