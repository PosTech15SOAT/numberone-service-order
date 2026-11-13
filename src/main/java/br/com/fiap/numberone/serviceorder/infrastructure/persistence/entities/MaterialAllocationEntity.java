package br.com.fiap.numberone.serviceorder.infrastructure.persistence.entities;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@jakarta.persistence.Entity
@lombok.Getter @lombok.Setter @lombok.NoArgsConstructor
@Table(name = "material_previsto")
public class MaterialAllocationEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_item_ordem_servico")
    private ServiceOrderItemEntity item;
    // External IDs only: deliberately no Inventory entity or foreign key.
    @Column(nullable = false) private UUID materialId;
    @Column(nullable = false, length = 256) private String description;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal referenceUnitPrice;
    @Column(nullable = false) private int plannedQuantity;
    // Kept for V1 audit and reconciliation.
    @Column(nullable = false) private int reservedQuantity;
    @Column(nullable = false) private int consumedQuantity;
    private UUID reservationId;
    private UUID consumptionOperationId;
    @ElementCollection
    @CollectionTable(name = "material_movement_reference", joinColumns = @JoinColumn(name = "allocation_id"))
    @Column(name = "movement_id", nullable = false)
    private List<UUID> movementIds = new ArrayList<>();
}
