package br.com.fiap.numberone.shared.api.exception;

import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderRuleException;
import br.com.fiap.numberone.serviceorder.domain.exceptions.OrderNotFoundException;
import br.com.fiap.numberone.serviceorder.application.gateways.MaterialReferenceUnavailableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.List;

/** No SQL messages, rejected values, personal data or exception stacks in responses/logs. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private ResponseEntity<ErrorResponse> error(int status, String code) {
        return ResponseEntity.status(status).body(new ErrorResponse(status, code, List.of()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> invalid(Exception ex) { return error(400, "INVALID_REQUEST"); }
    @ExceptionHandler({OrderNotFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ErrorResponse> missing(Exception ex) { return error(404, "RESOURCE_NOT_FOUND"); }
    @ExceptionHandler(OrderRuleException.class)
    ResponseEntity<ErrorResponse> rule(OrderRuleException ex) { return error(409, ex.getMessage()); }
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class})
    ResponseEntity<ErrorResponse> conflict(Exception ex) { return error(409, "PERSISTENCE_CONFLICT"); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> forbidden(Exception ex) { return error(403, "ACCESS_DENIED"); }
    @ExceptionHandler(MaterialReferenceUnavailableException.class)
    ResponseEntity<ErrorResponse> unavailable(Exception ex) { return error(503, "MATERIAL_REFERENCE_UNAVAILABLE"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex) { return error(500, "INTERNAL_ERROR"); }
}
