package br.com.fiap.numberone.billing;

import br.com.fiap.numberone.billing.payment.application.gateways.CheckoutGateway;
import br.com.fiap.numberone.billing.payment.application.gateways.OrderLookupGateway;
import br.com.fiap.numberone.billing.payment.application.results.CheckoutOrder;
import br.com.fiap.numberone.billing.payment.application.results.ProviderOrderSnapshot;
import br.com.fiap.numberone.billing.payment.application.results.ProviderPaymentSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@ConditionalOnProperty(name = "local.integration.fixture", havingValue = "true")
public class LocalMercadoPagoFixture {
    private final Map<String, ProviderOrderSnapshot> orders = new ConcurrentHashMap<>();

    @Bean @Primary
    CheckoutGateway fixtureCheckout() {
        return () -> (charge, attempt) -> {
            String id = "ORD" + attempt.toString().replace("-", "");
            orders.put(id, new ProviderOrderSnapshot(id, "created", "created", charge.getAmount(), null,
                    "BRL", attempt.toString(), List.of(), false));
            return new CheckoutOrder(id, URI.create("https://checkout.example.test/" + id), "created");
        };
    }

    @Bean @Primary
    OrderLookupGateway fixtureOrders() {
        return orders::get;
    }

    @RestController
    class FixtureController {
        @PutMapping("/fixture/provider/{id}/{status}")
        void setState(@PathVariable String id, @PathVariable String status) {
            var previous = orders.get(id);
            boolean refunded = status.equals("refunded");
            BigDecimal amount = previous.totalAmount();
            String detail = refunded ? "refunded" : "accredited";
            var payment = new ProviderPaymentSnapshot("PAY" + id, status, detail, amount, amount);
            orders.put(id, new ProviderOrderSnapshot(id, status, detail, amount, amount, "BRL",
                    previous.externalReference(), List.of(payment), refunded));
        }
    }
}
