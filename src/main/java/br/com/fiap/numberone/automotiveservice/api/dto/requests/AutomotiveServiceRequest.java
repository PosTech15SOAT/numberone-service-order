package br.com.fiap.numberone.automotiveservice.api.dto.requests;

import br.com.fiap.numberone.automotiveservice.domain.enums.ServiceType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutomotiveServiceRequest {

    @JsonProperty("codigo")
    @NotBlank(message = "O código do serviço é obrigatório")
    private String code;

    @JsonProperty("nome")
    @NotBlank(message = "O nome do serviço é obrigatório")
    private String name;

    @JsonProperty("descricao")
    @NotBlank(message = "A descrição do serviço é obrigatória")
    private String description;

    @JsonProperty("tipoServico")
    @NotNull(message = "O tipo do serviço é obrigatório")
    private ServiceType serviceType;

    @JsonProperty("valorBase")
    @NotNull(message = "O valor base do serviço é obrigatório")
    @DecimalMin(value = "0.01", inclusive = true, message = "O valor base do serviço deve ser maior que zero")
    private BigDecimal baseValue;

    @JsonProperty("tempoEstimadoMinutos")
    @NotNull(message = "O tempo estimado em minutos é obrigatório")
    @Min(value = 1, message = "O tempo estimado em minutos deve ser maior que zero")
    private Integer estimatedTimeMinutes;
}
