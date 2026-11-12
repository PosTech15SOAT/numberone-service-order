package br.com.fiap.numberone.serviceorder.application.gateways;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SagaRecoveryStore {
    List<UUID> dueCompensations();
    List<UUID> dueStarts();
    List<UUID> dueSettlementChecks();
    Optional<UUID> claimSettlementCheck(UUID orderId);
    void finishSettlementCheck(UUID orderId, UUID owner);
}
