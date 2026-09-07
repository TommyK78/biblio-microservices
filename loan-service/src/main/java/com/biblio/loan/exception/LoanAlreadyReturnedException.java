package com.biblio.loan.exception;

/** Tentative de rendre un emprunt deja RETURNED. Mappee en 409 Conflict. */
public class LoanAlreadyReturnedException extends RuntimeException {

    public LoanAlreadyReturnedException(Long loanId) {
        super("Cet emprunt est deja termine (" + loanId + ")");
    }
}
