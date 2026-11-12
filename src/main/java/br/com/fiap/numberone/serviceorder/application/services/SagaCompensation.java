package br.com.fiap.numberone.serviceorder.application.services;

import br.com.fiap.numberone.serviceorder.application.gateways.*;
import br.com.fiap.numberone.serviceorder.domain.entities.ServiceOrder;
import br.com.fiap.numberone.serviceorder.domain.enums.ServiceOrderStatus;
import br.com.fiap.numberone.serviceorder.domain.exceptions.*;
import br.com.fiap.numberone.shared.application.gateways.BranchScope;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;

/** C1-C6. The order lock serializes cancellation with confirmation of the first start. */
@Service
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class SagaCompensation {
    public record Request(@NotBlank @Pattern(regexp="FULL|PARTIAL") String scope,
                          @NotBlank @Size(max=1000) String reason, boolean unusedConfirmed,
                          boolean physicallyRemoved, boolean returnConfirmed,
                          @NotNull Instant verifiedAt, @NotBlank @Size(max=1000) String evidenceReference) { }
    public record Audit(Request request, UUID author, Instant acceptedAt, UUID correlationId,
                        Object composition, boolean automatic) { }
    public record Status(UUID serviceOrderId, String state, String reason, String returnState,
                         Object cancellation, Object reviewDecisions, List<Map<String,Object>> operations) { }
    private record Work(UUID id, UUID order, UUID original, String intent, String request, UUID budget, UUID correlation) { }
    private final JdbcTemplate jdbc;
    private final ServiceOrderGateway orders;
    private final BranchScope branch;
    private final InventorySagaGateway inventory;
    private final BillingSagaGateway billing;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public SagaCompensation(JdbcTemplate jdbc, ServiceOrderGateway orders, BranchScope branch,
                            InventorySagaGateway inventory, BillingSagaGateway billing, ObjectMapper json, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.orders=orders; this.branch=branch; this.inventory=inventory; this.billing=billing; this.json=json;
        tx=new TransactionTemplate(manager); tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW); tx.setTimeout(30);
    }
    private ServiceOrder load(UUID id, boolean scoped) {
        UUID branchId=scoped ? branch.id() : jdbc.queryForObject("SELECT branch_id FROM ordem_servico WHERE id=?", UUID.class,id);
        return orders.find(id,branchId,true).orElseThrow(OrderNotFoundException::new);
    }
    private Map<String,Object> saga(UUID id) {
        return jdbc.queryForMap("SELECT * FROM service_order_saga WHERE service_order_id=? FOR UPDATE",id);
    }
    private boolean preExecution(ServiceOrder o) {
        return Set.of(ServiceOrderStatus.RECEIVED,ServiceOrderStatus.IN_DIAGNOSIS,ServiceOrderStatus.WAITING_APPROVAL,
                ServiceOrderStatus.APPROVED,ServiceOrderStatus.REJECTED).contains(o.getStatus())
                && o.getItems().stream().noneMatch(i -> i.getStartDateTime()!=null);
    }
    public Status request(UUID id, Request request, UUID author, UUID correlation) {
        tx.executeWithoutResult(t -> {
            var o=load(id,true); var s=saga(id);
            if (s.get("cancellation")!=null) {
                var previous=json.readValue(s.get("cancellation").toString(),Audit.class);
                if (!previous.request().equals(request)) { throw new OrderRuleException("CANCELLATION_REQUEST_CONFLICT"); }
                return;
            }
            if (request.verifiedAt().isAfter(Instant.now().plusSeconds(30))) { throw new OrderRuleException("INVALID_VERIFICATION_TIME"); }
            boolean automatic=request.scope().equals("FULL") && request.unusedConfirmed()
                    && (!request.physicallyRemoved() || request.returnConfirmed()) && preExecution(o)
                    && !Set.of("MANUAL_INTERVENTION","COMPENSATING","COMPENSATED").contains(s.get("state"))
                    && o.getItems().stream().flatMap(i -> i.getMaterials().stream()).noneMatch(m -> m.hasMaterialEffect());
            var audit=new Audit(request,author,Instant.now(),correlation,o.compositionSnapshot(),automatic);
            jdbc.update("""
                    UPDATE service_order_saga SET cancellation=CAST(? AS jsonb),cancellation_in_progress=true,
                    return_state=state,state=?,reason=?,version=version+1,next_attempt_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                    WHERE service_order_id=?
                    """,json.writeValueAsString(audit),automatic ? "COMPENSATING" : "MANUAL_INTERVENTION",
                    automatic ? null : "OUTSIDE_AUTOMATIC_WINDOW",id);
            if (!automatic) { return; }
            // All original attempts are covered, including refused and currently in-flight ones.
            for (UUID original:jdbc.queryForList("SELECT operation_id FROM saga_operation WHERE service_order_id=? AND intent='START_ITEM' ORDER BY created_at,operation_id",UUID.class,id)) {
                UUID compensation=UUID.randomUUID();
                var reversal=new InventorySagaGateway.Reversal(compensation,request.physicallyRemoved() ? "VERIFIED_RETURN" : "CANCELLED_BEFORE_USE",
                        true,new InventorySagaGateway.Evidence(author,request.verifiedAt(),request.evidenceReference()));
                jdbc.update("""
                        INSERT INTO saga_operation(operation_id,service_order_id,intent,state,original_operation_id,request)
                        VALUES (?,?,'RESOLVE_STOCK','PENDING',?,CAST(? AS jsonb))
                        """,compensation,id,original,json.writeValueAsString(reversal));
            }
        });
        return status(id);
    }
    public Status status(UUID id) {
        return tx.execute(t -> {
            load(id,true); var s=saga(id);
            var operations=jdbc.queryForList("SELECT operation_id,original_operation_id,budget_id,budget_revision,intent,state,attempts,next_attempt_at,reason,result,late_result FROM saga_operation WHERE service_order_id=? ORDER BY created_at,operation_id",id);
            for (var operation:operations) {
                for (String field:List.of("result","late_result")) {
                    if (operation.get(field)!=null) { operation.put(field,json.readTree(operation.get(field).toString())); }
                }
            }
            return new Status(id,s.get("state").toString(),(String)s.get("reason"),(String)s.get("return_state"),
                    s.get("cancellation")==null ? null : json.readTree(s.get("cancellation").toString()),
                    json.readTree(s.get("review_decisions").toString()),operations);
        });
    }
    /** Audited retry of an eligible cancellation after an external conflict is resolved. No physical override. */
    public Status resume(UUID id, String decision, UUID author) {
        tx.executeWithoutResult(t -> {
            var o=load(id,true); var s=saga(id);
            if (!"MANUAL_INTERVENTION".equals(s.get("state")) || s.get("cancellation")==null
                    || !json.readValue(s.get("cancellation").toString(),Audit.class).automatic() || !preExecution(o)) {
                throw new OrderRuleException("OPERATIONAL_DECISION_REQUIRED");
            }
            jdbc.update("UPDATE service_order_saga SET review_decisions=review_decisions || CAST(? AS jsonb) WHERE service_order_id=?",
                    json.writeValueAsString(Map.of("author",author,"reason",decision,"previousReason",s.get("reason"),"at",Instant.now())),id);
            jdbc.update("UPDATE saga_operation SET state='PENDING',reason=NULL,next_attempt_at=CURRENT_TIMESTAMP WHERE service_order_id=? AND intent<>'START_ITEM' AND state='NEEDS_REVIEW'",id);
            jdbc.update("UPDATE service_order_saga SET state='COMPENSATING',reason=NULL,version=version+1,next_attempt_at=CURRENT_TIMESTAMP WHERE service_order_id=?",id);
        });
        return status(id);
    }
    /** One durable step per lease; a process can die at any boundary without losing identity. */
    public void recover(UUID id) {
        UUID owner=UUID.randomUUID();
        var work=tx.execute(t -> {
            var o=load(id,false); var s=saga(id);
            if (!"COMPENSATING".equals(s.get("state")) || s.get("cancellation")==null) { return null; }
            int claimed=jdbc.update("""
                    UPDATE service_order_saga SET recovery_owner=?,recovery_until=CURRENT_TIMESTAMP+interval '30 seconds',
                    recovery_attempts=recovery_attempts+1 WHERE service_order_id=? AND next_attempt_at<=CURRENT_TIMESTAMP
                    AND (recovery_until IS NULL OR recovery_until<CURRENT_TIMESTAMP)
                    """,owner,id);
            if (claimed==0) { return null; }
            var audit=json.readValue(s.get("cancellation").toString(),Audit.class);
            if (!preExecution(o) || !audit.automatic()) { review(id,null,"CANCELLATION_GUARDS_CHANGED"); return null; }
            var stock=jdbc.queryForList("SELECT operation_id FROM saga_operation WHERE service_order_id=? AND intent='RESOLVE_STOCK' AND state NOT IN ('SUCCEEDED','SKIPPED') ORDER BY created_at,operation_id LIMIT 1",UUID.class,id);
            UUID operation;
            if (!stock.isEmpty()) { operation=stock.getFirst(); }
            else {
                var existing=jdbc.queryForList("SELECT operation_id FROM saga_operation WHERE service_order_id=? AND intent IN ('CANCEL_BUDGET','CLOSE_BUDGET_CONTEXT')",UUID.class,id);
                if (existing.isEmpty()) {
                    operation=UUID.randomUUID();
                    Object request=o.getCurrentBudgetId()==null
                            ? new BillingSagaGateway.ContextCancellation(operation,"SERVICE_ORDER_CANCELLED")
                            : new BillingSagaGateway.Cancellation(operation,o.getBudgetRevision(),"SERVICE_ORDER_CANCELLED");
                    jdbc.update("""
                            INSERT INTO saga_operation(operation_id,service_order_id,intent,state,request,budget_revision,budget_id)
                            VALUES (?,?,?,'PENDING',CAST(? AS jsonb),?,?)
                            """,operation,id,o.getCurrentBudgetId()==null ? "CLOSE_BUDGET_CONTEXT" : "CANCEL_BUDGET",json.writeValueAsString(request),o.getBudgetRevision(),o.getCurrentBudgetId());
                } else { operation=existing.getFirst(); }
            }
            jdbc.update("UPDATE saga_operation SET attempts=attempts+1,updated_at=CURRENT_TIMESTAMP WHERE operation_id=?",operation);
            return jdbc.queryForObject("SELECT * FROM saga_operation WHERE operation_id=?",(r,n) -> new Work(
                    r.getObject("operation_id",UUID.class),id,r.getObject("original_operation_id",UUID.class),r.getString("intent"),r.getString("request"),r.getObject("budget_id",UUID.class),audit.correlationId()),operation);
        });
        if (work==null) { return; }
        try {
            Object result;
            if (work.intent().equals("RESOLVE_STOCK")) {
                var known=inventory.inventoryOperation(work.original(),work.correlation());
                if (Set.of("REJECTED","CLOSED","REVERSED").contains(known.result())) { result=known; }
                else {
                    try { result=inventory.reverse(work.original(),json.readValue(work.request(),InventorySagaGateway.Reversal.class),work.correlation()); }
                    catch (SagaConflictException ex) {
                        if (!"OPERATION_ALREADY_REJECTED".equals(ex.getMessage())) { throw ex; }
                        // A terminal refusal can win after the CT-08 read; confirm that fact.
                        var refused=inventory.inventoryOperation(work.original(),work.correlation());
                        if (!"REJECTED".equals(refused.result())) { throw new SagaDependencyException(); }
                        result=refused;
                    }
                }
                var resolved=(InventorySagaGateway.InventoryOperation)result;
                if (Set.of("CLOSED","REVERSED").contains(resolved.result()) && !work.id().equals(resolved.compensationId())) {
                    throw new SagaConflictException("COMPENSATION_ID_CONFLICT");
                }
            } else if (work.intent().equals("CANCEL_BUDGET")) {
                result=billing.cancelBudget(work.budget(),json.readValue(work.request(),BillingSagaGateway.Cancellation.class),work.correlation());
            } else { result=billing.closeBudgetContext(id,json.readValue(work.request(),BillingSagaGateway.ContextCancellation.class),work.correlation()); }
            Object confirmed=result;
            tx.executeWithoutResult(t -> {
                var o=load(id,false); var s=saga(id);
                if (!owner.equals(s.get("recovery_owner")) || !"COMPENSATING".equals(s.get("state"))) { return; }
                jdbc.update("UPDATE saga_operation SET state='SUCCEEDED',result=CAST(? AS jsonb),reason=NULL,updated_at=CURRENT_TIMESTAMP WHERE operation_id=?",json.writeValueAsString(confirmed),work.id());
                if (work.original()!=null) {
                    jdbc.update("UPDATE saga_operation SET state=CASE WHEN state='REJECTED' THEN state ELSE 'SKIPPED' END,reason='CANCELLATION_STOCK_RESOLVED',updated_at=CURRENT_TIMESTAMP WHERE operation_id=?",work.original());
                } else { finish(o); }
                release(id,owner,false);
            });
        } catch (SagaConflictException ex) {
            tx.executeWithoutResult(t -> {
                load(id,false); var s=saga(id);
                if (owner.equals(s.get("recovery_owner"))) { review(id,work.id(),ex.getMessage()); release(id,owner,false); }
            });
        } catch (SagaDependencyException ex) {
            tx.executeWithoutResult(t -> {
                load(id,false); var s=saga(id);
                if (!owner.equals(s.get("recovery_owner"))) { return; }
                jdbc.update("UPDATE saga_operation SET state='AWAITING_RESULT',reason='RESULT_UNKNOWN',next_attempt_at=CURRENT_TIMESTAMP+interval '5 seconds',updated_at=CURRENT_TIMESTAMP WHERE operation_id=?",work.id());
                release(id,owner,true);
            });
        }
    }
    private void finish(ServiceOrder o) {
        if (!preExecution(o) || jdbc.queryForObject("SELECT count(*) FROM saga_operation WHERE service_order_id=? AND state NOT IN ('SUCCEEDED','SKIPPED','REJECTED')",Integer.class,o.getId())!=0) {
            throw new OrderRuleException("COMPENSATION_EFFECTS_PENDING");
        }
        jdbc.update("UPDATE service_order_saga SET state='COMPENSATED',reason=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE service_order_id=?",o.getId());
        // Preserve immutable commercial snapshot and original movement audit.
        o.confirmCompensatedCancellation(); orders.save(o);
    }
    private void review(UUID id,UUID operation,String reason) {
        if (operation!=null) { jdbc.update("UPDATE saga_operation SET state='NEEDS_REVIEW',reason=?,updated_at=CURRENT_TIMESTAMP WHERE operation_id=?",reason,operation); }
        jdbc.update("UPDATE service_order_saga SET return_state=state,state='MANUAL_INTERVENTION',reason=?,version=version+1,recovery_owner=NULL,recovery_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE service_order_id=?",reason,id);
    }
    private void release(UUID id,UUID owner,boolean retry) {
        jdbc.update("UPDATE service_order_saga SET recovery_owner=NULL,recovery_until=NULL,next_attempt_at=CURRENT_TIMESTAMP+?*interval '1 second' WHERE service_order_id=? AND recovery_owner=?",retry ? 5 : 0,id,owner);
    }
}
