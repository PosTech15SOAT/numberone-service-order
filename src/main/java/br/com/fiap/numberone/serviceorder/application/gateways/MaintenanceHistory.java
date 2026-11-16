package br.com.fiap.numberone.serviceorder.application.gateways;

import br.com.fiap.numberone.serviceorder.domain.valueobjects.MaintenanceSnapshot;

import java.util.List;
import java.util.UUID;

public interface MaintenanceHistory extends AutoCloseable {
    enum Result { WRITTEN, REPLAYED, COLLISION }

    record Revision(UUID vehicleId, String key, UUID branchId, UUID orderId, int version, String snapshot) { }
    record Page(List<MaintenanceSnapshot> entries, String lastKey) { }

    Result put(Revision revision);
    Page query(UUID vehicle, UUID branch, int limit, String after);
    @Override void close();

    class Unavailable extends RuntimeException {
        public Unavailable(String message) { super(message); }
        public Unavailable(String message, Throwable cause) { super(message, cause); }
    }
}
