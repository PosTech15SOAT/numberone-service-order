package br.com.fiap.numberone.customer.domain.validators;

import br.com.fiap.numberone.customer.domain.enums.TipoDocumento;
import br.com.fiap.numberone.customer.domain.exceptions.CustomerDocumentException;

public final class DocumentoValidator {

    private DocumentoValidator() {
    }

    public static void validar(TipoDocumento tipoDocumento, String documento) {
        if (tipoDocumento == null) { throw new CustomerDocumentException("Tipo de documento obrigatorio"); }
        String numeroDocumento = somenteDigitos(documento);

        if (tipoDocumento == TipoDocumento.PESSOA_FISICA && !isCpfValido(numeroDocumento)) {
            throw new CustomerDocumentException("CPF invalido");
        }

        if (tipoDocumento == TipoDocumento.PESSOA_JURIDICA && !isCnpjValido(numeroDocumento)) {
            throw new CustomerDocumentException("CNPJ invalido");
        }
    }

    private static String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    private static boolean isCpfValido(String cpf) {
        if (cpf.length() != 11 || cpf.matches("(\\d)\\1{10}")) {
            return false;
        }

        int digito1 = calcularDigito(cpf, 9, 10);
        int digito2 = calcularDigito(cpf, 10, 11);

        return digito1 == Character.getNumericValue(cpf.charAt(9))
                && digito2 == Character.getNumericValue(cpf.charAt(10));
    }

    private static boolean isCnpjValido(String cnpj) {
        if (cnpj.length() != 14 || cnpj.matches("(\\d)\\1{13}")) {
            return false;
        }

        int digito1 = calcularDigitoCnpj(cnpj, 12);
        int digito2 = calcularDigitoCnpj(cnpj, 13);

        return digito1 == Character.getNumericValue(cnpj.charAt(12))
                && digito2 == Character.getNumericValue(cnpj.charAt(13));
    }

    private static int calcularDigito(String valor, int tamanho, int pesoInicial) {
        int soma = 0;
        int peso = pesoInicial;

        for (int i = 0; i < tamanho; i++) {
            soma += Character.getNumericValue(valor.charAt(i)) * peso--;
        }

        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static int calcularDigitoCnpj(String cnpj, int tamanho) {
        int soma = 0;
        int peso = 2;

        for (int i = tamanho - 1; i >= 0; i--) {
            soma += Character.getNumericValue(cnpj.charAt(i)) * peso;
            peso = (peso == 9) ? 2 : peso + 1;
        }

        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
