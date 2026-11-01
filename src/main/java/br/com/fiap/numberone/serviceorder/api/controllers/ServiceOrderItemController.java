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
    public ServiceOrderItemController(ServiceOrderService service, ServiceOrderApiMapper mapper) {
        this.service = service; this.mapper = mapper;
    }
    @PostMapping public ResponseEntity<OrderResponses.Item> create(@Valid @RequestBody OrderRequests.Item r) {
        var item = service.addItem(r.orderId(), r.serviceId(), r.estimate(), r.quantity() == null ? 1 : r.quantity(), Boolean.TRUE.equals(r.optional()));
        return ResponseEntity.created(URI.create("/api/admin/itens-ordem-servico/" + item.getId())).body(mapper.toResponse(item));
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> remove(@PathVariable UUID id) { service.removeItem(id); return ResponseEntity.noContent().build(); }
    @PatchMapping("/{id}/iniciar") public void start(@PathVariable UUID id) { service.requestItemStart(id); }
    @PatchMapping("/{id}/cancelar") public OrderResponses.Item cancel(@PathVariable UUID id) { return mapper.toResponse(service.cancelItem(id)); }
    @PatchMapping("/{id}/concluir") public OrderResponses.Item complete(@PathVariable UUID id) { return mapper.toResponse(service.completeItem(id)); }
}
