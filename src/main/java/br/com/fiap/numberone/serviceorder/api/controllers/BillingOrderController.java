package br.com.fiap.numberone.serviceorder.api.controllers;

import br.com.fiap.numberone.serviceorder.application.gateways.ServiceOrderGateway;
import br.com.fiap.numberone.serviceorder.domain.entities.CompositionSnapshot;
import br.com.fiap.numberone.serviceorder.domain.exceptions.*;
import br.com.fiap.numberone.shared.application.gateways.BranchScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/internal/service-orders")
@Transactional
public class BillingOrderController {
    private final ServiceOrderGateway orders;
    private final BranchScope branch;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public BillingOrderController(ServiceOrderGateway orders, BranchScope branch, JdbcTemplate jdbc, ObjectMapper json) {
        this.orders = orders; this.branch = branch; this.jdbc = jdbc; this.json = json;
    }
    @GetMapping("/{id}")
    public Map<String,Object> state(@PathVariable UUID id) {
        var o = orders.find(id, branch.id(), true).orElseThrow(OrderNotFoundException::new);
        boolean cancelling = Boolean.TRUE.equals(jdbc.queryForObject("SELECT cancellation_in_progress OR state='COMPENSATING' FROM service_order_saga WHERE service_order_id=?", Boolean.class, id));
        return Map.of("serviceOrderId", id, "status", o.getStatus().name(),
                "updatedAt", o.getUpdatedAt().toInstant(ZoneOffset.UTC), "cancellationInProgress", cancelling);
    }
    @GetMapping("/{id}/quotation-context")
    public Map<String,Object> composition(@PathVariable UUID id, @RequestParam(required=false) Integer compositionVersion) {
        var o = orders.find(id, branch.id(), true).orElseThrow(OrderNotFoundException::new);
        int version = compositionVersion == null ? o.getCompositionVersion() : compositionVersion;
        if (version < 1) { throw new IllegalArgumentException(); }
        var snapshots = jdbc.queryForList("SELECT items::text FROM service_order_composition WHERE service_order_id=? AND composition_version=?", String.class, id, version);
        if (snapshots.isEmpty()) { throw new OrderNotFoundException(); }
        var items = json.readValue(snapshots.getFirst(), CompositionSnapshot.Item[].class);
        if (items.length == 0) { throw new OrderRuleException("EMPTY_COMPOSITION"); }
        return Map.of("serviceOrderId", id, "status", o.getStatus().name(), "compositionVersion", version, "items", items);
    }
}
