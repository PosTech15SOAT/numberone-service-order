package br.com.fiap.numberone.shared.api.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record ErrorResponse(
        int status,
        String message,
        List<String> errors,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code
) {
    public ErrorResponse(int status, String message, List<String> errors) {
        this(status, message, errors, null);
    }
}
