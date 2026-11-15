package br.com.fiap.numberone.serviceorder.application.gateways;

import br.com.fiap.numberone.serviceorder.domain.valueobjects.MaintenanceSnapshot;

public interface MaintenanceHistoryGateway {
    /** Records the snapshot in the ongoing service order completion transaction. */
    void append(MaintenanceSnapshot snapshot);
}
