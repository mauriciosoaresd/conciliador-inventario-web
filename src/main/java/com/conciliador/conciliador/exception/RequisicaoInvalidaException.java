package com.conciliador.conciliador.exception;

public class RequisicaoInvalidaException extends RuntimeException {

    public RequisicaoInvalidaException(String message) {
        super(message);
    }

    public RequisicaoInvalidaException(String message, Throwable cause) {
        super(message, cause);
    }
}
