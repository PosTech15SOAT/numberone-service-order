package br.com.fiap.numberone.vehicle.application.gateways;

import java.util.UUID;

public interface CustomerGateway {

    boolean existsById(UUID id);
}
