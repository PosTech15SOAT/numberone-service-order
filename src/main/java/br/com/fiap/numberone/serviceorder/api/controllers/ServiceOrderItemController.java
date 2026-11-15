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
    private final br.com.fiap.numberone.serviceorder.application.services.StartServiceOrderItem saga;
    private final tools.jackson.databind.ObjectMapper json;
    public ServiceOrderItemController(ServiceOrderService service, ServiceOrderApiMapper mapper,
            br.com.fiap.numberone.serviceorder.application.services.StartServiceOrderItem saga,
            tools.jackson.databind.ObjectMapper json) {
        this.service = service; this.mapper = mapper;
        this.saga = saga; this.json = json;
    }
    @PostMapping public ResponseEntity<OrderResponses.Item> create(@Valid @RequestBody OrderRequests.Item r) {
        var item = service.addItem(r.orderId(), r.serviceId(), r.estimate(), r.quantity() == null ? 1 : r.quantity(), Boolean.TRUE.equals(r.optional()));
        return ResponseEntity.created(URI.create("/api/admin/itens-ordem-servico/" + item.getId())).body(mapper.toResponse(item));
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> remove(@PathVariable UUID id) { service.removeItem(id); return ResponseEntity.noContent().build(); }
    @PatchMapping("/{id}/iniciar") public ResponseEntity<tools.jackson.databind.JsonNode> start(@PathVariable UUID id,
            @RequestHeader(value="Idempotency-Key", required=false) UUID key,
            @RequestHeader("X-Authenticated-Subject") String subject) {
        UUID author = br.com.fiap.numberone.shared.api.DeclaredAuthor.parse(subject);
        var correlation = br.com.fiap.numberone.shared.infrastructure.correlation.SagaCorrelation.current();
        var result = saga.start(id, key, author, correlation);
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
