package br.com.fiap.numberone.serviceorder.infrastructure.persistence.entities;

import br.com.fiap.numberone.serviceorder.domain.enums.OrderItemStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@jakarta.persistence.Entity
@lombok.Getter @lombok.Setter @lombok.NoArgsConstructor
@Table(name = "item_ordem_servico")
public class ServiceOrderItemEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_ordem_servico")
    private ServiceOrderEntity order;
    @Column(name = "id_servico", nullable = false) private UUID automotiveServiceId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_servico", insertable = false, updatable = false)
    private br.com.fiap.numberone.automotiveservice.infrastructure.persistence.entities.AutomotiveServiceEntity automotiveService;
    @Column(nullable = false, length = 256) private String serviceDescription;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal laborUnitPrice;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false) private int estimatedTimeMinutes;
    @Column(nullable = false) private boolean optional;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private OrderItemStatus status;
    @Column(nullable = false) private boolean startPending;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private UUID startOperationId;
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MaterialAllocationEntity> materials = new ArrayList<>();
}
