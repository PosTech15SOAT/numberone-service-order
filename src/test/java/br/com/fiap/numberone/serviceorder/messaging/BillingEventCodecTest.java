package br.com.fiap.numberone.serviceorder.messaging;

import br.com.fiap.numberone.serviceorder.infrastructure.messaging.BillingEventCodec;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class BillingEventCodecTest {
    private final BillingEventCodec codec = new BillingEventCodec();
    private final Path root = Path.of("doc/contracts/saga/v2");
    @Test void acceptsAllProducerFixturesIncludingAdditiveFields() throws Exception {
        try (var files = Files.list(root.resolve("examples"))) {
            for (var file : files.toList()) { assertThat(codec.decode(Files.readString(file)).hash()).hasSize(64); }
        }
    }
    @Test void rejectsEveryInvalidContractExampleOffline() throws Exception {
        try (var files = Files.list(root.resolve("invalid-examples"))) {
            for (var file : files.filter(f -> !f.getFileName().toString().equals("expectations.json")).toList()) {
                assertThatThrownBy(() -> codec.decode(Files.readString(file))).as(file.getFileName().toString())
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }
    @Test void validatesEnvelopeAndCanonicalizesFullContent() throws Exception {
        var body = Files.readString(root.resolve("examples/OrcamentoEnviado.json"));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var original = mapper.readTree(body);
        var reordered = mapper.createObjectNode();
        var fields = new java.util.ArrayList<String>(); original.fieldNames().forEachRemaining(fields::add);
        java.util.Collections.reverse(fields); fields.forEach(f -> reordered.set(f, original.get(f)));
        assertThat(codec.decode(mapper.writeValueAsString(reordered)).hash()).isEqualTo(codec.decode(body).hash());
        reordered.put("futureOptional", true);
        assertThat(codec.decode(mapper.writeValueAsString(reordered)).hash()).isNotEqualTo(codec.decode(body).hash());
        reordered.set("futureOptional", mapper.readTree("{\"n\":1.0,\"a\":[null,false,\"value\"]}"));
        assertThat(codec.decode(mapper.writeValueAsString(reordered)).hash()).hasSize(64);
        for (var invalid : java.util.List.of("null", "[]", "{", "{}", body.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
                body.replace("\"budgetVersion\": 1", "\"budgetVersion\": 99999999999999999999"),
                body.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"schemaVersion\": 1"))) {
            assertThatThrownBy(() -> codec.decode(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
