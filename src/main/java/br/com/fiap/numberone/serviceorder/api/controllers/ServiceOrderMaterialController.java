package br.com.fiap.numberone.serviceorder.api.controllers;

import br.com.fiap.numberone.serviceorder.api.dto.OrderRequests;
import br.com.fiap.numberone.serviceorder.api.dto.OrderResponses;
import br.com.fiap.numberone.serviceorder.api.mappers.ServiceOrderApiMapper;
import br.com.fiap.numberone.serviceorder.application.services.ServiceOrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/itens-ordem-servico/{itemId}/insumos")
public class ServiceOrderMaterialController {
    private final ServiceOrderService service;
    private final ServiceOrderApiMapper mapper;
    public ServiceOrderMaterialController(ServiceOrderService service, ServiceOrderApiMapper mapper) {
        this.service = service; this.mapper = mapper;
    }
    @PostMapping public ResponseEntity<OrderResponses.Material> create(@PathVariable UUID itemId, @Valid @RequestBody OrderRequests.Material r) {
        var m = service.putMaterial(itemId, null, r.materialId(), r.plannedQuantity());
        return ResponseEntity.created(URI.create("/api/admin/itens-ordem-servico/" + itemId + "/insumos/" + m.getId())).body(mapper.toResponse(m));
    }
    @PutMapping("/{id}") public OrderResponses.Material update(@PathVariable UUID itemId, @PathVariable UUID id, @Valid @RequestBody OrderRequests.Material r) {
        return mapper.toResponse(service.putMaterial(itemId, id, r.materialId(), r.plannedQuantity()));
    }
    @GetMapping public List<OrderResponses.Material> list(@PathVariable UUID itemId) { return service.listMaterials(itemId).stream().map(mapper::toResponse).toList(); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> remove(@PathVariable UUID itemId, @PathVariable UUID id) {
        service.removeMaterial(itemId, id); return ResponseEntity.noContent().build();
    }
}
