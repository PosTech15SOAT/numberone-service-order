package br.com.fiap.numberone.serviceorder.infrastructure.history;

import br.com.fiap.numberone.serviceorder.domain.valueobjects.MaintenanceSnapshot;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DynamoMaintenanceHistory {
    private static final String VEHICLE = "vehicleId";
    private static final String KEY = "historyKey";
    private static final String BRANCH = "branchId";
    private static final String SNAPSHOT = "snapshot";

    private final DynamoDbClient dynamo;
    private final String table;
    private final ObjectMapper json;

    DynamoMaintenanceHistory(DynamoDbClient dynamo, String table, ObjectMapper json) {
        this.dynamo = dynamo;
        this.table = table;
        this.json = json;
    }

    enum Result { WRITTEN, REPLAYED, COLLISION }

    Result put(MaintenanceHistoryOutbox.Claim claim) {
        var key = key(claim.vehicleId(), claim.key());
        var item = Map.of(VEHICLE, key.get(VEHICLE), KEY, key.get(KEY),
                BRANCH, AttributeValue.fromS(claim.branchId().toString()),
                "serviceOrderId", AttributeValue.fromS(claim.orderId().toString()),
                "historyVersion", AttributeValue.fromN(Integer.toString(claim.version())),
                SNAPSHOT, AttributeValue.fromS(claim.snapshot()));
        try {
            dynamo.putItem(request -> request.tableName(table).item(item)
                    .conditionExpression("attribute_not_exists(#vehicle)")
                    .expressionAttributeNames(Map.of("#vehicle", VEHICLE)));
            return Result.WRITTEN;
        } catch (ConditionalCheckFailedException ex) {
            var existing = dynamo.getItem(request -> request.tableName(table).key(key).consistentRead(true)).item();
            return item.equals(existing) ? Result.REPLAYED : Result.COLLISION;
        }
    }

    public record Page(List<MaintenanceSnapshot> entries, String lastKey) { }

    public Page query(UUID vehicle, UUID branch, int limit, String after) {
        var request = QueryRequest.builder().tableName(table).limit(50).scanIndexForward(false).consistentRead(true)
                .keyConditionExpression("#vehicle = :vehicle").filterExpression("#branch = :branch")
                .expressionAttributeNames(Map.of("#vehicle", VEHICLE, "#branch", BRANCH))
                .expressionAttributeValues(Map.of(":vehicle", AttributeValue.fromS(vehicle.toString()),
                        ":branch", AttributeValue.fromS(branch.toString())));
        if (after != null) {
            request.exclusiveStartKey(key(vehicle, after));
        }
        do {
            var response = dynamo.query(request.build());
            var entries = response.items().stream().limit(limit).map(item -> readSnapshot(item, vehicle, branch)).toList();
            boolean hasMore = !response.lastEvaluatedKey().isEmpty();
            if (!entries.isEmpty()) {
                // The public cursor may only contain the key of a document visible in this branch.
                String lastKey = hasMore || response.count() > limit ? entries.getLast().historyKey() : null;
                return new Page(entries, lastKey);
            }
            if (!hasMore) {
                return new Page(List.of(), null);
            }
            request.exclusiveStartKey(response.lastEvaluatedKey());
        } while (true);
    }

    private MaintenanceSnapshot readSnapshot(Map<String, AttributeValue> item, UUID vehicle, UUID branch) {
        var snapshot = json.readValue(item.get(SNAPSHOT).s(), MaintenanceSnapshot.class);
        if (!vehicle.equals(snapshot.vehicleId()) || !branch.equals(snapshot.branchId())
                || !snapshot.historyKey().equals(item.get(KEY).s())) {
            throw new IllegalStateException("INVALID_MAINTENANCE_HISTORY_DOCUMENT");
        }
        return snapshot;
    }

    private static Map<String, AttributeValue> key(UUID vehicle, String historyKey) {
        return Map.of(VEHICLE, AttributeValue.fromS(vehicle.toString()), KEY, AttributeValue.fromS(historyKey));
    }
}
