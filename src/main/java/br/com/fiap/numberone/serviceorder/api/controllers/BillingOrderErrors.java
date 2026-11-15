package br.com.fiap.numberone.serviceorder.api.controllers;

import br.com.fiap.numberone.serviceorder.domain.exceptions.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(assignableTypes = BillingOrderController.class)
@Order(0)
public class BillingOrderErrors {
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,Object>> error(Exception ex, HttpServletRequest request) {
        int status = ex instanceof OrderNotFoundException ? 404 : ex instanceof OrderRuleException ? 409
                : ex instanceof IllegalArgumentException || ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ? 400 : 503;
        String code = status == 404 ? "NOT_FOUND" : status == 409 ? ex.getMessage() : status == 400 ? "INVALID_REQUEST" : "DEPENDENCY_UNAVAILABLE";
        return ResponseEntity.status(status).body(Map.of("code", code, "message", code, "correlationId", request.getHeader("X-Correlation-Id")));
    }
}
