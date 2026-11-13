package br.com.fiap.numberone.vehicle.api.exceptions;

import br.com.fiap.numberone.shared.api.exception.ErrorResponse;
import br.com.fiap.numberone.vehicle.domain.exceptions.VehicleCustomerNotFoundException;
import br.com.fiap.numberone.vehicle.domain.exceptions.VehicleLicensePlateAlreadyExistsException;
import br.com.fiap.numberone.vehicle.domain.exceptions.VehicleNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VehicleExceptionHandler {

    @ExceptionHandler({VehicleNotFoundException.class, VehicleCustomerNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                List.of()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(VehicleLicensePlateAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleBusinessValidation(VehicleLicensePlateAlreadyExistsException ex) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.UNPROCESSABLE_CONTENT.value(),
                ex.getMessage(),
                List.of()
        );

        return ResponseEntity.unprocessableContent().body(response);
    }
}
