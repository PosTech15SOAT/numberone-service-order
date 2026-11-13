package br.com.fiap.numberone.serviceorder.infrastructure.integration;

import br.com.fiap.numberone.serviceorder.application.gateways.MaterialReferenceGateway;
import br.com.fiap.numberone.serviceorder.application.gateways.MaterialReferenceUnavailableException;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;

@Configuration
public class MaterialReferenceConfiguration {
    @Bean
    @ConditionalOnProperty(name = "app.material-reference.provider", havingValue = "http")
    MaterialReferenceGateway httpMaterialReference(@Qualifier("inventorySagaHttp") ParticipantHttpClient inventory,
                                               ObjectMapper json) {
        return new HttpMaterialReferenceGateway(inventory, json);
    }

    @Bean
    @ConditionalOnProperty(name = "app.material-reference.provider", havingValue = "unavailable", matchIfMissing = true)
    MaterialReferenceGateway unavailableMaterialReference() {
        return id -> { throw new MaterialReferenceUnavailableException(); };
    }

    @Bean
    @Profile("(local | test) & !production")
    @ConditionalOnProperty(name = "app.material-reference.provider", havingValue = "fixture")
    MaterialReferenceGateway fixtureMaterialReference() {
        // Explicit demonstration fixture. No Inventory database, balance, reservation or consumption.
        UUID materialId = UUID.fromString("80000000-0000-4000-8000-000000000001");
        return id -> {
            if (!materialId.equals(id)) { throw new OrderNotFoundException(); }
            return new MaterialReferenceGateway.MaterialReference(id, "Kit demonstrativo", new BigDecimal("200.00"), true);
        };
    }
}
