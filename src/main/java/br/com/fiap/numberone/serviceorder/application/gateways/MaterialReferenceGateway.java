package br.com.fiap.numberone.serviceorder.application.gateways;

import java.math.BigDecimal;
import java.util.UUID;

/** Reference only; availability is advisory and must never be treated as a reservation. */
public interface MaterialReferenceGateway {
    MaterialReference find(UUID materialId);
    record MaterialReference(UUID id, String description, BigDecimal referenceUnitPrice, boolean active) { }
}
