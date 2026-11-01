package br.com.fiap.numberone.customer.api.dtos.requests;

import br.com.fiap.numberone.customer.domain.enums.TipoDocumento;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @JsonProperty("nome")
        @NotBlank(message = "Nome e obrigatorio")
        @Size(max = 90, message = "Nome deve ter no maximo 90 caracteres")
        String name,

        @JsonProperty("tipoDocumento")
        @NotNull(message = "Tipo de documento e obrigatorio")
        TipoDocumento documentType,

        @JsonProperty("documento")
        @NotBlank(message = "Documento e obrigatorio")
        @Size(max = 50, message = "Documento deve ter no maximo 50 caracteres")
        String document,

        @JsonProperty("email")
        @NotBlank(message = "Email e obrigatorio")
        @Email(message = "Email deve ser valido")
        @Size(max = 120, message = "Email deve ter no maximo 120 caracteres")
        String email,

        @JsonProperty("telefone")
        @NotBlank(message = "Telefone e obrigatorio")
        @Size(max = 15, message = "Telefone deve ter no maximo 15 caracteres")
        String phone,

        @JsonProperty("endereco")
        @NotBlank(message = "Endereco e obrigatorio")
        @Size(max = 90, message = "Endereco deve ter no maximo 90 caracteres")
        String address,

        @JsonProperty("ativo")
        Boolean active
) {
}
