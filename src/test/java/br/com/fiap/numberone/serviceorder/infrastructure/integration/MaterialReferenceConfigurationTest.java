package br.com.fiap.numberone.serviceorder.infrastructure.integration;

import br.com.fiap.numberone.serviceorder.application.gateways.MaterialReferenceUnavailableException;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialReferenceConfigurationTest {
    @Test void defaultAdapterNeverFabricatesInventoryAvailability() {
        var gateway = new MaterialReferenceConfiguration().unavailableMaterialReference();
        assertThatThrownBy(() -> gateway.find(UUID.randomUUID())).isInstanceOf(MaterialReferenceUnavailableException.class);
    }
}
