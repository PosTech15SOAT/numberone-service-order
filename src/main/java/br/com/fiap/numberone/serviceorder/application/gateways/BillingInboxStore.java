package br.com.fiap.numberone.serviceorder.application.gateways;

import br.com.fiap.numberone.serviceorder.application.events.BillingEvent;
import br.com.fiap.numberone.serviceorder.application.gateways.OrderCompletionStore.SagaSnapshot;
import br.com.fiap.numberone.serviceorder.domain.entities.ServiceOrder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface BillingInboxStore {
    record EventContext(ServiceOrder order, SagaSnapshot saga) { }

    record RecordedPayment(UUID orderId, int version, String status) { }

    Optional<String> findProcessedHashForUpdate(UUID eventId);
    EventContext begin(BillingEvent event);
    void complete(UUID eventId, BillingEvent.Outcome outcome);
    Optional<RecordedPayment> findPayment(UUID paymentId);
    LocalDateTime savePaymentAndFindLatestPaid(BillingEvent event, BillingEvent.Payment payment);
    void scheduleSettlementCheck(UUID orderId);
}
