package com.biblio.loan.exception;

/**
 * Plus aucun exemplaire disponible. Levee dans deux situations :
 *  - la verification prealable de loan-service (etape 1) a vu availableCopies == 0 ;
 *  - book-service a repondu 409 lors du decrement (etape 2, cas de concurrence).
 * Dans les deux cas le client recoit un 409 Conflict.
 */
public class NoCopyAvailableException extends RuntimeException {

    public NoCopyAvailableException(Long bookId) {
        super("Aucun exemplaire disponible pour ce livre (" + bookId + ")");
    }
}
