package br.com.fiap.numberone.serviceorder.api.controllers;

import br.com.fiap.numberone.serviceorder.application.gateways.SagaDependencyException;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderNotFoundException;
import br.com.fiap.numberone.serviceorder.domain.valueobjects.MaintenanceSnapshot;
import br.com.fiap.numberone.serviceorder.infrastructure.history.DynamoMaintenanceHistory;
import br.com.fiap.numberone.shared.application.gateways.BranchScope;
import br.com.fiap.numberone.shared.security.infrastructure.authorization.AuthenticatedCustomerAccess;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.core.exception.SdkException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
public class MaintenanceHistoryController {
    private static final Pattern HISTORY_KEY = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z#[0-9a-f-]{36}#[1-9][0-9]{0,9}");

    private final JdbcTemplate jdbc;
    private final BranchScope branch;
    private final AuthenticatedCustomerAccess access;
    private final ObjectProvider<DynamoMaintenanceHistory> history;

    public MaintenanceHistoryController(JdbcTemplate jdbc, BranchScope branch, AuthenticatedCustomerAccess access,
                                        ObjectProvider<DynamoMaintenanceHistory> history) {
        this.jdbc = jdbc;
        this.branch = branch;
        this.access = access;
        this.history = history;
    }

    public record HistoryPage(List<MaintenanceSnapshot> entries, String nextCursor) { }

    @GetMapping("/api/public/veiculos/{vehicleId}/historico-manutencoes")
    public HistoryPage history(@PathVariable UUID vehicleId, @RequestParam(defaultValue = "20") int limit,
                               @RequestParam(required = false) String cursor) {
        UUID branchId = branch.id();
        UUID customerId = jdbc.queryForList("""
                SELECT v.id_cliente FROM veiculo v WHERE v.id=? AND EXISTS (
                    SELECT 1 FROM ordem_servico o WHERE o.id_veiculo=v.id AND o.branch_id=?
                )
                """, UUID.class, vehicleId, branchId).stream().findFirst().orElseThrow(OrderNotFoundException::new);
        access.requireCurrentUserOwnershipOrAdmin(customerId);
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("INVALID_HISTORY_LIMIT");
        }
        String scope = vehicleId + "#" + branchId + "#";
        String after = cursor == null ? null : decode(cursor, scope);
        var store = history.getIfAvailable();
        if (store == null) {
            throw new SagaDependencyException();
        }
        try {
            var page = store.query(vehicleId, branchId, limit, after);
            String nextCursor = page.lastKey() == null ? null : Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((scope + page.lastKey()).getBytes(StandardCharsets.US_ASCII));
            return new HistoryPage(page.entries(), nextCursor);
        } catch (SdkException ex) {
            throw new SagaDependencyException();
        }
    }

    private static String decode(String cursor, String scope) {
        if (cursor.length() > 220) {
            throw new IllegalArgumentException("INVALID_HISTORY_CURSOR");
        }
        String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
        if (!decoded.startsWith(scope)) {
            throw new IllegalArgumentException("INVALID_HISTORY_CURSOR");
        }
        String key = decoded.substring(scope.length());
        if (!HISTORY_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("INVALID_HISTORY_CURSOR");
        }
        return key;
    }
}
