package com.biblio.loan.exception;

/**
 * book-service est injoignable ou a repondu une erreur inattendue (5xx, timeout).
 * Mappee en 503 Service Unavailable : le probleme ne vient pas du client.
 */
public class BookServiceUnavailableException extends RuntimeException {

    public BookServiceUnavailableException(Throwable cause) {
        super("Le service des livres est momentanement indisponible", cause);
    }
}
