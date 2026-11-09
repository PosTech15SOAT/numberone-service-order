package br.com.fiap.numberone.serviceorder.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Schemas are bundled from the active package; resolution never uses the network. */
@Component
public class BillingEventCodec {
    private final ObjectMapper json = new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final Map<String, JsonSchema> schemas = new HashMap<>();
    public record Event(UUID id, String type, UUID orderId, UUID correlationId,
                        Instant occurredAt, JsonNode payload, String hash) { }

    public BillingEventCodec() {
        try {
            var selection = read("../packages.json");
            if (!selection.path("activeVersion").asText().equals("2.0.0-os-baseline.1")
                    || !selection.path("readyForOsImplementation").asBoolean()) {
                throw new IllegalStateException("INACTIVE_OS_CONTRACT");
            }
            var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            var config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
            for (var entry : read("catalog.json").path("messages")) {
                schemas.put(entry.path("type").asText(), factory.getSchema(expand(read(entry.path("schema").asText())), config));
            }
        } catch (IOException ex) { throw new IllegalStateException("CONTRACT_RESOURCE_UNAVAILABLE", ex); }
    }

    public Event decode(String body) {
        try {
            var root = json.readTree(body);
            if (root == null || !root.isObject()) { throw new IllegalArgumentException("INVALID_ENVELOPE"); }
            var schema = schemas.get(root.path("eventType").asText());
            if (schema == null || !schema.validate(root).isEmpty()) { throw new IllegalArgumentException("INVALID_BILLING_EVENT"); }
            var p = root.path("payload");
            for (var field : List.of("budgetVersion", "compositionVersion", "paymentVersion")) {
                if (p.has(field) && !p.path(field).canConvertToInt()) { throw new IllegalArgumentException("REVISION_OUT_OF_RANGE"); }
            }
            var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(canonical(root)).getBytes(StandardCharsets.UTF_8)));
            return new Event(UUID.fromString(root.path("eventId").asText()), root.path("eventType").asText(),
                    UUID.fromString(p.path("serviceOrderId").asText()), UUID.fromString(root.path("correlationId").asText()),
                    Instant.parse(root.path("occurredAt").asText()), p, hash);
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalArgumentException("INVALID_BILLING_EVENT", ex); }
    }

    private JsonNode read(String file) throws IOException {
        String path = file.equals("../packages.json") ? "/contracts/saga/packages.json" : "/contracts/saga/v2/" + file;
        try (var stream = getClass().getResourceAsStream(path)) {
            if (stream == null) { throw new IOException("Missing bundled contract " + path); }
            return json.readTree(stream);
        }
    }
    private JsonNode expand(JsonNode node) throws IOException {
        if (node.isObject()) {
            if (node.has("$ref")) {
                var ref = node.path("$ref").asText();
                var local = ref.substring(ref.indexOf("/saga/v2/") + 9).split("#", 2);
                var target = read(local[0]);
                return expand(local.length == 2 ? target.at(local[1]) : target);
            }
            var result = json.createObjectNode();
            var fields = node.fields();
            while (fields.hasNext()) {
                var f = fields.next();
                if (!f.getKey().equals("$id")) { result.set(f.getKey(), expand(f.getValue())); }
            }
            return result;
        }
        if (node.isArray()) {
            var result = json.createArrayNode();
            for (var child : node) { result.add(expand(child)); }
            return result;
        }
        return node;
    }
    private Object canonical(JsonNode node) {
        if (node.isObject()) {
            var result = new TreeMap<String, Object>();
            node.fields().forEachRemaining(f -> result.put(f.getKey(), canonical(f.getValue())));
            return result;
        }
        if (node.isArray()) { var result = new ArrayList<>(); node.forEach(n -> result.add(canonical(n))); return result; }
        if (node.isNumber()) { return node.decimalValue().stripTrailingZeros(); }
        if (node.isBoolean()) { return node.booleanValue(); }
        return node.isNull() ? null : node.textValue();
    }
}
