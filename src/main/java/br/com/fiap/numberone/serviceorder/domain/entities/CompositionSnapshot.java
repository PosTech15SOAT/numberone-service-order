package br.com.fiap.numberone.serviceorder.domain.entities;

import java.util.List;
import java.util.UUID;

public record CompositionSnapshot(UUID serviceOrderId, int version, List<Item> items) {
    public CompositionSnapshot {
        items = List.copyOf(items);
    }

    public record Item(UUID serviceOrderItemId, UUID automotiveServiceId, int quantity,
            boolean optional, String serviceAmount, List<Material> supplies) {
        public Item {
            supplies = List.copyOf(supplies);
        }
    }

    public record Material(UUID inventoryItemId, int quantity) { }
}
