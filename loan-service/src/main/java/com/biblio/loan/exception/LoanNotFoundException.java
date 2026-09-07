package com.biblio.loan.exception;

/** L'emprunt demande n'existe pas. Mappee en 404 Not Found. */
public class LoanNotFoundException extends RuntimeException {

    public LoanNotFoundException(Long id) {
        super("Emprunt introuvable avec l'identifiant " + id);
    }
}
