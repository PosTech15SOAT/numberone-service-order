package br.com.fiap.numberone.automotiveservice.api.controllers;

import br.com.fiap.numberone.automotiveservice.api.dto.requests.AutomotiveServiceRequest;
import br.com.fiap.numberone.automotiveservice.api.dto.responses.AutomotiveServiceResponse;
import br.com.fiap.numberone.automotiveservice.api.mappers.AutomotiveServiceApiMapper;
import br.com.fiap.numberone.automotiveservice.application.services.AutomotiveServiceService;
import br.com.fiap.numberone.automotiveservice.domain.entities.AutomotiveService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/servicos")
public class AutomotiveServiceController {

    private final AutomotiveServiceService autoServiceService;
    private final AutomotiveServiceApiMapper autoServiceApiMapper;

    public AutomotiveServiceController(AutomotiveServiceService autoServiceService, AutomotiveServiceApiMapper autoServiceApiMapper) {
        this.autoServiceService = autoServiceService;
        this.autoServiceApiMapper = autoServiceApiMapper;
    }

    @PostMapping
    public ResponseEntity<AutomotiveServiceResponse> create(@RequestBody @Valid AutomotiveServiceRequest request) {
        AutomotiveService servico = autoServiceService.create(autoServiceApiMapper.toDomain(request));

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(servico.getId())
                .toUri();

        return ResponseEntity.created(location).body(autoServiceApiMapper.toResponse(servico));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AutomotiveServiceResponse> update(@PathVariable UUID id,
                                     @RequestBody @Valid AutomotiveServiceRequest request) {

        AutomotiveService updatedService = autoServiceService.update(id, autoServiceApiMapper.toDomain(request));
        return  ResponseEntity.ok(autoServiceApiMapper.toResponse(updatedService));
    }

    @GetMapping
    public ResponseEntity<List<AutomotiveServiceResponse>> findAll() {
        return ResponseEntity.ok(autoServiceService.findAll()
                .stream()
                .map(autoServiceApiMapper::toResponse)
                .toList());
    }

    @GetMapping("/{id}")
    public AutomotiveServiceResponse findById(@PathVariable UUID id) {
        return autoServiceApiMapper.toResponse(autoServiceService.findById(id));
    }

    @PatchMapping("/{id}/inativar")
    public ResponseEntity<Void> inactivate(@PathVariable UUID id) {
        autoServiceService.inactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/ativar")
    public ResponseEntity<Void> activate(@PathVariable UUID id) {
        autoServiceService.activate(id);
        return ResponseEntity.noContent().build();
    }
}
