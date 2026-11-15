package br.com.fiap.numberone.automotiveservice.api.dto.responses;

import br.com.fiap.numberone.automotiveservice.domain.enums.ServiceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutomotiveServiceResponse {

    private UUID id;
    private String code;
    private String name;
    private String description;
    private ServiceType serviceType;
    private BigDecimal baseValue;
    private Integer estimatedTimeMinutes;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
