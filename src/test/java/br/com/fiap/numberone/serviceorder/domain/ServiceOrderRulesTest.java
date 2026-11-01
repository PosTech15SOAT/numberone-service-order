package br.com.fiap.numberone.serviceorder.domain;

import br.com.fiap.numberone.serviceorder.domain.entities.*;
import br.com.fiap.numberone.serviceorder.domain.enums.*;
import br.com.fiap.numberone.serviceorder.domain.exceptions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ServiceOrderRulesTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 12, 0);

    private ServiceOrder.ServiceOrderBuilder order(ServiceOrderStatus status) {
        return ServiceOrder.builder().id(UUID.randomUUID()).status(status).entryDateTime(NOW.minusHours(1));
    }
    private ServiceOrder.ServiceOrderBuilder paid(ServiceOrderStatus status) {
        return order(status).approvedBudgetId(UUID.randomUUID()).approvedRevision(1).approvedCurrency("BRL")
                .approvedAmount(new BigDecimal("350.00")).approvedItemsJson("[]").paymentConfirmedAt(NOW);
    }
    private ServiceOrderItem.ServiceOrderItemBuilder item(OrderItemStatus status) {
        return ServiceOrderItem.builder().id(UUID.randomUUID()).status(status).laborUnitPrice(new BigDecimal("150.00"));
    }
    private MaterialAllocation.MaterialAllocationBuilder material() {
        return MaterialAllocation.builder().id(UUID.randomUUID()).materialId(UUID.randomUUID()).description("Fixture")
                .referenceUnitPrice(new BigDecimal("200.00")).plannedQuantity(1);
    }
    private MaterialAllocation consumed() {
        return material().reservedQuantity(1).consumedQuantity(1).reservationId(UUID.randomUUID())
                .consumptionOperationId(UUID.randomUUID()).movementIds(List.of(UUID.randomUUID())).build();
    }

    @Test void diagnosisCanStartAndBeEditedBeforeApproval() {
        var o = order(ServiceOrderStatus.RECEIVED).build();
        o.diagnose("Diagnostico", NOW.plusHours(1), "Fixture");
        o.diagnose("Diagnostico revisado", NOW.plusHours(2), null);
        assertThat(o.getStatus()).isEqualTo(ServiceOrderStatus.IN_DIAGNOSIS);
        assertThat(o.getFinalDiagnosisDescription()).isEqualTo("Diagnostico revisado");
        assertThatThrownBy(() -> o.diagnose(" ", NOW, null)).hasMessage("INVALID_DIAGNOSIS");
        assertThatThrownBy(() -> o.diagnose("Fixture", NOW.minusDays(1), null)).hasMessage("INVALID_DIAGNOSIS");
    }

    @ParameterizedTest @EnumSource(value = ServiceOrderStatus.class, names = {"RECEIVED","IN_DIAGNOSIS"}, mode = EnumSource.Mode.EXCLUDE)
    void diagnosisCannotChangeAfterLeavingDiagnosis(ServiceOrderStatus status) {
        assertThatThrownBy(() -> order(status).build().diagnose("Fixture", NOW, null)).hasMessage("DIAGNOSIS_NOT_EDITABLE");
    }

    @Test void approvedSnapshotFreezesEvenAnInconsistentDiagnosisState() {
        var o = paid(ServiceOrderStatus.IN_DIAGNOSIS).build();
        assertThatThrownBy(o::requireEditableDiagnosis).hasMessage("APPROVED_SNAPSHOT_IMMUTABLE");
        assertThatThrownBy(() -> o.diagnose("Fixture", NOW, null)).hasMessage("APPROVED_SNAPSHOT_IMMUTABLE");
        assertThatThrownBy(o::cancel).hasMessage("APPROVED_SNAPSHOT_IMMUTABLE");
    }

    @ParameterizedTest @EnumSource(ServiceOrderStatus.class)
    void noOrderStartsWithoutPaymentConfirmation(ServiceOrderStatus status) {
        assertThatThrownBy(() -> order(status).build().requestStart()).hasMessage("PAYMENT_NOT_CONFIRMED");
    }

    @Test void startIsBlockedEvenWhenFixtureContainsPayment() {
        assertThatThrownBy(() -> paid(ServiceOrderStatus.READY_FOR_EXECUTION).build().requestStart())
                .hasMessage("EXECUTION_INTEGRATION_NOT_AVAILABLE");
        assertThatThrownBy(() -> paid(ServiceOrderStatus.IN_DIAGNOSIS).build().requestStart())
                .hasMessage("ORDER_NOT_READY_FOR_EXECUTION");
        assertThatThrownBy(() -> paid(ServiceOrderStatus.READY_FOR_EXECUTION).approvedRevision(2).build().requestStart())
                .hasMessage("PAYMENT_NOT_CONFIRMED");
    }

    @ParameterizedTest @EnumSource(value = OrderItemStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
    void onlyStartedItemsCanComplete(OrderItemStatus status) {
        var i = item(status).build();
        assertThatThrownBy(() -> i.complete(NOW)).hasMessage("ITEM_START_NOT_CONFIRMED");
    }

    @Test void materialPlanOrReservationDoesNotCountAsConsumption() {
        var i = item(OrderItemStatus.IN_PROGRESS).startDateTime(NOW.minusMinutes(1))
                .materials(new ArrayList<>(List.of(material().reservedQuantity(1).reservationId(UUID.randomUUID()).build()))).build();
        assertThatThrownBy(() -> i.complete(NOW)).hasMessage("MATERIAL_CONSUMPTION_PENDING");
        i.getMaterials().clear(); i.getMaterials().add(consumed());
        i.complete(NOW);
        assertThat(i.getStatus()).isEqualTo(OrderItemStatus.COMPLETED);
    }

    @Test void completionAndDeliveryRequireAllEligibleItemsFinished() {
        var i = item(OrderItemStatus.IN_PROGRESS).startDateTime(NOW.minusMinutes(10)).build();
        var o = paid(ServiceOrderStatus.IN_PROGRESS).items(new ArrayList<>(List.of(i))).build();
        assertThatThrownBy(o::complete).hasMessage("ORDER_HAS_UNFINISHED_ITEMS");
        assertThatThrownBy(() -> o.deliver(NOW)).hasMessage("ORDER_NOT_COMPLETED");
        o.completeItem(i.getId(), NOW);
        o.complete(); o.deliver(NOW.plusMinutes(1));
        assertThat(o.getStatus()).isEqualTo(ServiceOrderStatus.DELIVERED);
        assertThat(o.getDeliveryDateTime()).isEqualTo(NOW.plusMinutes(1));
    }

    @Test void pendingStartAndCancelledUnreleasedMaterialPreventCompletion() {
        var pending = item(OrderItemStatus.START_REQUESTED).build();
        var o = paid(ServiceOrderStatus.IN_PROGRESS).items(List.of(pending)).build();
        assertThatThrownBy(o::complete).hasMessage("ORDER_HAS_UNFINISHED_ITEMS");
        var completed = item(OrderItemStatus.COMPLETED).startDateTime(NOW.minusHours(1)).endDateTime(NOW).build();
        var cancelled = item(OrderItemStatus.CANCELLED).materials(List.of(consumed())).build();
        var blocked = paid(ServiceOrderStatus.IN_PROGRESS).items(List.of(completed, cancelled)).build();
        assertThatThrownBy(blocked::complete).hasMessage("MATERIAL_COMPENSATION_PENDING");
    }

    @ParameterizedTest @EnumSource(value = ServiceOrderStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
    void onlyCompletedOrdersCanBeDelivered(ServiceOrderStatus status) {
        assertThatThrownBy(() -> order(status).build().deliver(NOW)).hasMessage("ORDER_NOT_COMPLETED");
    }

    @Test void estimateIsLocalAndDoesNotSetApprovedAmount() {
        var i = item(OrderItemStatus.PENDING).materials(new ArrayList<>(List.of(material().build()))).build();
        var o = order(ServiceOrderStatus.IN_DIAGNOSIS).build(); o.addItem(i);
        assertThat(o.estimate()).isEqualByComparingTo("350.00");
        assertThat(o.getApprovedAmount()).isNull();
        i.cancelBeforeApproval();
        assertThat(o.estimate()).isEqualByComparingTo("0.00");
    }

    @Test void cancelledOrderCannotBeDeliveredAndCannotBeEdited() {
        var o = order(ServiceOrderStatus.IN_DIAGNOSIS).items(new ArrayList<>(List.of(item(OrderItemStatus.PENDING).build()))).build();
        o.cancel();
        assertThat(o.getItems().getFirst().getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
        assertThatThrownBy(() -> o.addItem(item(OrderItemStatus.PENDING).build())).hasMessage("DIAGNOSIS_NOT_EDITABLE");
        assertThatThrownBy(() -> o.deliver(NOW)).hasMessage("ORDER_NOT_COMPLETED");
    }

    @Test void allocationsAreScopedToAnItemAndRejectDuplicates() {
        var i = item(OrderItemStatus.PENDING).build(); var o = order(ServiceOrderStatus.IN_DIAGNOSIS).build(); o.addItem(i);
        var m = material().build(); o.putMaterial(i.getId(), m, false);
        assertThatThrownBy(() -> o.putMaterial(i.getId(), material().materialId(m.getMaterialId()).build(), false))
                .hasMessage("DUPLICATE_MATERIAL");
        assertThatThrownBy(() -> o.putMaterial(i.getId(), material().build(), true)).isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> o.putMaterial(UUID.randomUUID(), material().build(), false)).isInstanceOf(OrderNotFoundException.class);
        o.putMaterial(i.getId(), material().id(m.getId()).plannedQuantity(2).build(), true);
        o.removeMaterial(i.getId(), m.getId()); o.removeItem(i.getId());
        assertThat(o.getItems()).isEmpty();
    }

    @Test void allocatedMaterialCannotBeErasedByEditingOrCancellingDiagnosis() {
        var m = consumed();
        var i = item(OrderItemStatus.PENDING).materials(new ArrayList<>(List.of(m))).build();
        var o = order(ServiceOrderStatus.IN_DIAGNOSIS).items(new ArrayList<>(List.of(i))).build();
        assertThatThrownBy(() -> o.removeItem(i.getId())).hasMessage("MATERIAL_RECONCILIATION_REQUIRED");
        assertThatThrownBy(() -> o.removeMaterial(i.getId(), m.getId())).hasMessage("MATERIAL_RECONCILIATION_REQUIRED");
        assertThatThrownBy(() -> o.putMaterial(i.getId(), material().id(m.getId()).build(), true)).hasMessage("MATERIAL_RECONCILIATION_REQUIRED");
        assertThatThrownBy(i::cancelBeforeApproval).hasMessage("MATERIAL_RECONCILIATION_REQUIRED");
        assertThatThrownBy(o::cancel).hasMessage("CANCELLATION_REQUIRES_RECONCILIATION");
        assertThat(i.getMaterials()).containsExactly(m);
    }
}
