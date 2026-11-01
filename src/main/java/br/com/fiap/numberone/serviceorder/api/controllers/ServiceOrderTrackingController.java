package br.com.fiap.numberone.serviceorder.api.controllers;

import br.com.fiap.numberone.serviceorder.api.dto.OrderResponses;
import br.com.fiap.numberone.serviceorder.api.mappers.ServiceOrderApiMapper;
import br.com.fiap.numberone.serviceorder.application.services.ServiceOrderService;
import br.com.fiap.numberone.shared.security.infrastructure.authorization.AuthenticatedCustomerAccess;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/ordens-servico")
public class ServiceOrderTrackingController {
    private final ServiceOrderService service;
    private final ServiceOrderApiMapper mapper;
    private final AuthenticatedCustomerAccess access;
    public ServiceOrderTrackingController(ServiceOrderService service, ServiceOrderApiMapper mapper, AuthenticatedCustomerAccess access) {
        this.service = service; this.mapper = mapper; this.access = access;
    }
    @GetMapping("/{id}/acompanhamento")
    public OrderResponses.Order tracking(@PathVariable UUID id) {
        var order = service.get(id);
        access.requireCurrentUserOwnershipOrAdmin(order.getCustomerId());
        return mapper.toResponse(order);
    }
}
