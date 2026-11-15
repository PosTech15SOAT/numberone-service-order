package br.com.fiap.numberone.serviceorder.api.controllers;

import br.com.fiap.numberone.serviceorder.api.dto.OrderResponses;
import br.com.fiap.numberone.serviceorder.api.mappers.ServiceOrderApiMapper;
import br.com.fiap.numberone.serviceorder.application.services.ServiceOrderService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/ordens-servico")
public class ServiceOrderTrackingController {
    private final ServiceOrderService service;
    private final ServiceOrderApiMapper mapper;
    public ServiceOrderTrackingController(ServiceOrderService service, ServiceOrderApiMapper mapper) {
        this.service = service; this.mapper = mapper;
    }
    @GetMapping("/{id}/acompanhamento")
    public OrderResponses.Order tracking(@PathVariable UUID id) {
        var order = service.get(id);
        return mapper.toResponse(order);
    }
}
