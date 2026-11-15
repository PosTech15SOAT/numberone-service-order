package br.com.fiap.numberone.serviceorder.application.events;

import java.time.Instant;
import java.util.UUID;

public record BillingEvent(UUID id, Kind kind, UUID orderId, UUID correlationId, Instant occurredAt,
                           Payload payload, String hash) {
    public enum Kind {
        BUDGET_SENT("OrcamentoEnviado"),
        BUDGET_APPROVED("OrcamentoAprovado"),
        BUDGET_REJECTED("OrcamentoRejeitado"),
        PAYMENT_CONFIRMED("PagamentoConfirmado"),
        PAYMENT_UPDATED("PagamentoAtualizado");

        private final String wireName;

        Kind(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
    }

    public enum Outcome { APPLIED, DUPLICATE, OBSOLETE, IGNORED }

    public sealed interface Payload permits Budget, Payment { }

    public record Budget(UUID id, int version, int compositionVersion, String approvedAmount, String currency)
            implements Payload { }

    public record Payment(UUID id, int version, String status) implements Payload { }
}
