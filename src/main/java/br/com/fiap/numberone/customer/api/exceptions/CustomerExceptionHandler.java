package br.com.fiap.numberone.customer.api.exceptions;

import br.com.fiap.numberone.customer.domain.exceptions.CustomerDocumentException;
import br.com.fiap.numberone.customer.domain.exceptions.CustomerNotFoundException;
import br.com.fiap.numberone.shared.api.exception.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CustomerExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(CustomerNotFoundException ex) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(CustomerDocumentException.class)
    public ResponseEntity<ErrorResponse> handleBusinessValidation(CustomerDocumentException ex) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.UNPROCESSABLE_CONTENT.value(),
                ex.getMessage(),
                List.of()
        );

        return ResponseEntity.unprocessableContent().body(response);
    }
}
