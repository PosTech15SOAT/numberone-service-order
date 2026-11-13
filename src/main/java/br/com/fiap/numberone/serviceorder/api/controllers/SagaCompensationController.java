package br.com.fiap.numberone.serviceorder.api.controllers;

import br.com.fiap.numberone.serviceorder.application.services.SagaCompensation;
import br.com.fiap.numberone.serviceorder.application.gateways.SagaCompensationStore.Request;
import br.com.fiap.numberone.serviceorder.application.gateways.SagaCompensationStore.Status;
import br.com.fiap.numberone.serviceorder.application.gateways.ServiceOrderGateway;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderNotFoundException;
import br.com.fiap.numberone.shared.application.gateways.BranchScope;
import br.com.fiap.numberone.shared.security.application.gateways.AuthenticatedUserProvider;
import br.com.fiap.numberone.shared.infrastructure.correlation.SagaCorrelation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class SagaCompensationController {
    private final SagaCompensation compensation;
    private final AuthenticatedUserProvider users;
    private final ServiceOrderGateway orders;
    private final BranchScope branch;
    public SagaCompensationController(SagaCompensation compensation,AuthenticatedUserProvider users,ServiceOrderGateway orders,BranchScope branch) {
        this.compensation=compensation; this.users=users; this.orders=orders; this.branch=branch;
    }
    private UUID author() {
        return UUID.fromString(users.currentUser().orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("UNAUTHENTICATED")).subject());
    }
    @PatchMapping("/ordens-servico/{id}/cancelar")
    public ResponseEntity<Status> cancel(@PathVariable UUID id,@Valid @RequestBody Request request) {
        return response(compensation.request(id,request,author(),SagaCorrelation.current()));
    }
    @PatchMapping("/itens-ordem-servico/{id}/cancelar")
    public ResponseEntity<Status> partial(@PathVariable UUID id,@Valid @RequestBody Request request) {
        UUID order=orders.findByItem(id,branch.id(),false).orElseThrow(OrderNotFoundException::new).getId();
        var partial=new Request("PARTIAL",request.reason(),request.unusedConfirmed(),request.physicallyRemoved(),
                request.returnConfirmed(),request.verifiedAt(),"item="+id+"; "+request.evidenceReference());
        return response(compensation.request(order,partial,author(),SagaCorrelation.current()));
    }
    @GetMapping("/ordens-servico/{id}/compensacao")
    public Status status(@PathVariable UUID id) { return compensation.status(id); }
    public record Decision(@NotBlank @Size(max=1000) String reason) { }
    @PostMapping("/ordens-servico/{id}/compensacao/retomar")
    public ResponseEntity<Status> resume(@PathVariable UUID id,@Valid @RequestBody Decision decision) {
        return response(compensation.resume(id,decision.reason(),author()));
    }
    private ResponseEntity<Status> response(Status status) {
        return ResponseEntity.status(status.state().equals("MANUAL_INTERVENTION") ? 409 : status.state().equals("COMPENSATED") ? 200 : 202)
                .location(URI.create("/api/admin/ordens-servico/"+status.serviceOrderId()+"/compensacao")).body(status);
    }
}
