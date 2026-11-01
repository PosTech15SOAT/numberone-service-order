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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import static br.com.fiap.numberone.serviceorder.domain.enums.OrderItemStatus.*;

@RestController
@RequestMapping("/api/admin/ordens-servico")
public class ServiceOrderController {
    private final ServiceOrderService service;
    private final ServiceOrderApiMapper mapper;
    public ServiceOrderController(ServiceOrderService service, ServiceOrderApiMapper mapper) {
        this.service = service; this.mapper = mapper;
    }
    @PostMapping
    public ResponseEntity<OrderResponses.Order> create(@Valid @RequestBody OrderRequests.Create r) {
        var order = service.create(r.customerId(), r.vehicleId(), r.description(), r.diagnosis(), r.notes(), r.entry());
        return ResponseEntity.created(URI.create("/api/admin/ordens-servico/" + order.getId())).body(mapper.toResponse(order));
    }
    @GetMapping public List<OrderResponses.Order> list() { return service.list().stream().map(mapper::toResponse).toList(); }
    @GetMapping("/{id}") public OrderResponses.Order get(@PathVariable UUID id) { return mapper.toResponse(service.get(id)); }
    @PatchMapping({"/{id}/iniciar-diagnostico", "/{id}/diagnostico"})
    public OrderResponses.Order diagnose(@PathVariable UUID id, @Valid @RequestBody OrderRequests.Diagnosis r) {
        return mapper.toResponse(service.diagnose(id, r.description(), r.expected(), r.notes()));
    }
    @GetMapping("/{id}/calcular-servicos")
    public OrderResponses.Estimate estimate(@PathVariable UUID id) {
        return new OrderResponses.Estimate("ESTIMATIVA", "BRL", service.get(id).estimate());
    }
    @PatchMapping("/{id}/iniciar") public void start(@PathVariable UUID id) { service.requestStart(id); }
    @GetMapping("/{id}/calcular-tempo-estimado") public OrderResponses.EstimatedTime estimatedTime(@PathVariable UUID id) {
        long minutes = service.get(id).estimatedMinutes();
        return new OrderResponses.EstimatedTime(id, minutes, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(minutes));
    }
    @GetMapping("/{id}/tempo-medio-execucao-servicos") public OrderResponses.ExecutionTime executionTime(@PathVariable UUID id) {
        var o = service.get(id);
        return new OrderResponses.ExecutionTime(id, o.countItems(COMPLETED), o.countItems(PENDING),
                o.countItems(IN_PROGRESS), o.countItems(CANCELLED), o.countItems(WAITING_FOR_PARTS_AND_SUPPLIES),
                o.averageExecutionMinutes(), o.countItems(START_REQUESTED));
    }
    @PatchMapping("/{id}/cancelar") public OrderResponses.Order cancel(@PathVariable UUID id) { return mapper.toResponse(service.cancel(id)); }
    @PatchMapping("/{id}/concluir") public OrderResponses.Order complete(@PathVariable UUID id) { return mapper.toResponse(service.complete(id)); }
    @PatchMapping("/{id}/entregar") public OrderResponses.Order deliver(@PathVariable UUID id) { return mapper.toResponse(service.deliver(id)); }
}
