package br.com.fiap.numberone.serviceorder.infrastructure.persistence.entities;

import br.com.fiap.numberone.serviceorder.domain.enums.ServiceOrderStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@jakarta.persistence.Entity
@lombok.Getter @lombok.Setter @lombok.NoArgsConstructor
@Table(name = "ordem_servico")
public class ServiceOrderEntity {
    @Id private UUID id;
    @Version private Long version;
    @Column(nullable = false) private UUID branchId;
    @Column(name = "id_cliente", nullable = false) private UUID customerId;
    @Column(name = "id_veiculo", nullable = false) private UUID vehicleId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_cliente", insertable = false, updatable = false)
    private br.com.fiap.numberone.customer.infrastructure.persistence.entities.CustomerEntity customer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_veiculo", insertable = false, updatable = false)
    private br.com.fiap.numberone.vehicle.infrastructure.persistence.entities.VehicleEntity vehicle;
    @Column(nullable = false) private UUID attemptId;
    @Column(nullable = false) private int budgetRevision;
    @Column(nullable = false) private int compositionVersion;
    private UUID currentBudgetId;
    private Integer budgetCompositionVersion;
    @Column(nullable = false, length = 2000) private String initialDescription;
    @Column(nullable = false, length = 2000) private String diagnosisDescription;
    @Column(length = 2000) private String finalDiagnosisDescription;
    @Column(length = 2000) private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ServiceOrderStatus status;
    @Column(nullable = false) private LocalDateTime entryDateTime;
    private LocalDateTime expectedDateTime;
    private LocalDateTime deliveryDateTime;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    private UUID approvedBudgetId;
    private Integer approvedRevision;
    @Column(length = 3) private String approvedCurrency;
    @Column(precision = 19, scale = 2) private BigDecimal approvedAmount;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb") private String approvedItemsJson;
    private Integer approvedCompositionVersion;
    private LocalDateTime paymentConfirmedAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceOrderItemEntity> items = new ArrayList<>();
}
