package br.com.fiap.numberone.shared.api.exception;

import java.util.List;

public record ErrorResponse(
        int status,
        String message,
        List<String> errors
) {}
