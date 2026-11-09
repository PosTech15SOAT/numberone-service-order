package br.com.fiap.numberone.serviceorder.api.controllers;

import br.com.fiap.numberone.serviceorder.api.dto.OrderRequests;
import br.com.fiap.numberone.serviceorder.api.dto.OrderResponses;
import br.com.fiap.numberone.serviceorder.api.mappers.ServiceOrderApiMapper;
import br.com.fiap.numberone.serviceorder.application.services.ServiceOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/itens-ordem-servico")
public class ServiceOrderItemController {
    private final ServiceOrderService service;
    private final ServiceOrderApiMapper mapper;
    private final br.com.fiap.numberone.serviceorder.application.services.SagaWorkflow saga;
    private final br.com.fiap.numberone.shared.security.application.gateways.AuthenticatedUserProvider users;
    private final tools.jackson.databind.ObjectMapper json;
    public ServiceOrderItemController(ServiceOrderService service, ServiceOrderApiMapper mapper,
            br.com.fiap.numberone.serviceorder.application.services.SagaWorkflow saga,
            br.com.fiap.numberone.shared.security.application.gateways.AuthenticatedUserProvider users,
            tools.jackson.databind.ObjectMapper json) {
        this.service = service; this.mapper = mapper;
        this.saga = saga; this.users = users; this.json = json;
    }
    @PostMapping public ResponseEntity<OrderResponses.Item> create(@Valid @RequestBody OrderRequests.Item r) {
        var item = service.addItem(r.orderId(), r.serviceId(), r.estimate(), r.quantity() == null ? 1 : r.quantity(), Boolean.TRUE.equals(r.optional()));
        return ResponseEntity.created(URI.create("/api/admin/itens-ordem-servico/" + item.getId())).body(mapper.toResponse(item));
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> remove(@PathVariable UUID id) { service.removeItem(id); return ResponseEntity.noContent().build(); }
    @PatchMapping("/{id}/iniciar") public ResponseEntity<tools.jackson.databind.JsonNode> start(@PathVariable UUID id,
            @RequestHeader(value="Idempotency-Key", required=false) UUID key) {
        var user = users.currentUser().orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("UNAUTHENTICATED"));
        // CT-05 requires the authenticated employee UUID, never an ID supplied by the body.
        var correlation = br.com.fiap.numberone.shared.infrastructure.correlation.SagaCorrelation.current();
        var result = saga.start(id, key, UUID.fromString(user.subject()), correlation);
        if (result.state().equals("REJECTED")) {
            return ResponseEntity.status(409).body(json.valueToTree(java.util.Map.of("status", 409,
                    "message", "INSUFFICIENT_STOCK", "code", "INSUFFICIENT_STOCK", "errors", java.util.List.of(),
                    "operationId", result.operationId(), "correlationId", correlation)));
        }
        var body = (tools.jackson.databind.node.ObjectNode) json.valueToTree(mapper.toResponse(result.order().item(id)));
        body.put("idOrdemServico", result.order().getId().toString());
        body.put("startOperationId", result.operationId().toString());
        body.put("operationState", result.state());
        boolean confirmed = result.state().equals("CONFIRMED");
        return ResponseEntity.status(confirmed ? 200 : 202)
                .location(URI.create("/api/public/ordens-servico/" + result.order().getId() + "/acompanhamento")).body(body);
    }
    @PatchMapping("/{id}/concluir") public OrderResponses.Item complete(@PathVariable UUID id) { return mapper.toResponse(service.completeItem(id)); }
}
