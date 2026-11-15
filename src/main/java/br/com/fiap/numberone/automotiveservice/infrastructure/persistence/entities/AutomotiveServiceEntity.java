package br.com.fiap.numberone.automotiveservice.infrastructure.persistence.entities;

import br.com.fiap.numberone.automotiveservice.domain.enums.ServiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "servico_automotivo")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutomotiveServiceEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @jakarta.persistence.Version
    private Long version;

    @Column(name = "codigo", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "nome", nullable = false, length = 150)
    private String name;

    @Column(name = "descricao", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_servico")
    private ServiceType serviceType;

    @Column(name = "valor_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseValue;

    @Column(name = "tempo_estimado_minutos", nullable = false)
    private Integer estimatedTimeMinutes;

    @Column(name = "ativo", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;

        if (this.active == null) {
            this.active = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
