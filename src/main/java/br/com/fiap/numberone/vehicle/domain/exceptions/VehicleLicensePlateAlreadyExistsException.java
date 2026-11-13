package br.com.fiap.numberone.vehicle.domain.exceptions;

public class VehicleLicensePlateAlreadyExistsException extends RuntimeException {

    public VehicleLicensePlateAlreadyExistsException(String message) {
        super(message);
    }
}
