package br.com.fiap.numberone.shared.api.controllers;

import br.com.fiap.numberone.shared.api.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/public/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "numberone-service-order");
    }
}
