package br.com.fiap.numberone.vehicle.domain.exceptions;

public class VehicleCustomerNotFoundException extends RuntimeException {

    public VehicleCustomerNotFoundException(String message) {
        super(message);
    }
}
