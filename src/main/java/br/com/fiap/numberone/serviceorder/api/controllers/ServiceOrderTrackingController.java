package br.com.fiap.numberone.serviceorder.api.controllers;

import br.com.fiap.numberone.serviceorder.api.dto.OrderResponses;
import br.com.fiap.numberone.serviceorder.api.mappers.ServiceOrderApiMapper;
import br.com.fiap.numberone.serviceorder.application.services.ServiceOrderService;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/public/ordens-servico")
public class ServiceOrderTrackingController {
    private final ServiceOrderService service;
    private final ServiceOrderApiMapper mapper;

    public ServiceOrderTrackingController(ServiceOrderService service, ServiceOrderApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/{id}/acompanhamento")
    public OrderResponses.Order tracking(@PathVariable UUID id, HttpServletRequest request) {
        UUID customer = TrackingAccess.customerScope(request);
        var order = service.get(id);
        if (customer != null && !customer.equals(order.getCustomerId())) {
            throw new OrderNotFoundException();
        }
        return mapper.toResponse(order);
    }
}
