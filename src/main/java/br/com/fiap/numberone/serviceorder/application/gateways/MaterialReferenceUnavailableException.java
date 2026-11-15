package br.com.fiap.numberone.serviceorder.application.gateways;

public class MaterialReferenceUnavailableException extends RuntimeException {
    public MaterialReferenceUnavailableException() { super("MATERIAL_REFERENCE_UNAVAILABLE"); }
}
