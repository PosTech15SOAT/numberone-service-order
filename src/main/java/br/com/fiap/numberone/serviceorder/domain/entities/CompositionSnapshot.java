package br.com.fiap.numberone.serviceorder.domain.entities;

import br.com.fiap.numberone.serviceorder.domain.enums.OrderItemStatus;

import java.util.List;
import java.util.UUID;

public record CompositionSnapshot(UUID serviceOrderId, int version, List<Item> items) {
    public CompositionSnapshot {
        items = List.copyOf(items);
    }

    public boolean sameComposition(CompositionSnapshot other) {
        return serviceOrderId.equals(other.serviceOrderId) && version == other.version
                && items.stream().map(Item::quotedScope).toList()
                        .equals(other.items.stream().map(Item::quotedScope).toList());
    }

    public record Item(UUID serviceOrderItemId, UUID automotiveServiceId, int quantity,
            boolean optional, String serviceAmount, List<Material> supplies, OrderItemStatus status) {
        public Item {
            supplies = List.copyOf(supplies);
            // As versões antigas já excluíam itens cancelados.
            status = status == null ? OrderItemStatus.PENDING : status;
        }

        private Item quotedScope() {
            return new Item(serviceOrderItemId, automotiveServiceId, quantity, optional, serviceAmount,
                    supplies, OrderItemStatus.PENDING);
        }
    }

    public record Material(UUID inventoryItemId, int quantity) { }
}
