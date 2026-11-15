package br.com.fiap.numberone.customer.api.dtos.responses;

import br.com.fiap.numberone.customer.domain.enums.TipoDocumento;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerResponse(
        @JsonProperty("id")
        UUID id,
        @JsonProperty("nome")
        String name,
        @JsonProperty("tipoDocumento")
        TipoDocumento documentType,
        @JsonProperty("documento")
        String document,
        @JsonProperty("email")
        String email,
        @JsonProperty("telefone")
        String phone,
        @JsonProperty("endereco")
        String address,
        @JsonProperty("ativo")
        Boolean active,
        @JsonProperty("criadoEm")
        LocalDateTime createdAt,
        @JsonProperty("atualizadoEm")
        LocalDateTime updatedAt
) {
}
