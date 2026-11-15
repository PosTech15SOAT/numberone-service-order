package br.com.fiap.numberone.automotiveservice.api.exceptions;

import br.com.fiap.numberone.automotiveservice.domain.exceptions.*;

import br.com.fiap.numberone.shared.api.exception.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

@RestControllerAdvice(basePackages = "br.com.fiap.numberone.automotiveservice")
public class AutomotiveServiceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AutomotiveServiceExceptionHandler.class);
    private static final int UNPROCESSABLE_ENTITY = 422;

    @ExceptionHandler(AutoServiceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAutoServiceNotFoundException(
            AutoServiceNotFoundException ex
    ) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler({
            AutomotiveServiceBusinessException.class,
            InvalidAutomotiveServiceDataException.class,
            AutomotiveServiceAlreadyInactiveException.class,
            AutomotiveServiceAlreadyActiveException.class
    })
    public ResponseEntity<ErrorResponse> handleAutomotiveServiceBusinessExceptions(
            RuntimeException ex
    ) {

        ErrorResponse response = new ErrorResponse(
                UNPROCESSABLE_ENTITY,
                ex.getMessage(),
                List.of()
        );

        return ResponseEntity.status(HttpStatusCode.valueOf(UNPROCESSABLE_ENTITY)).body(response);
    }

}
