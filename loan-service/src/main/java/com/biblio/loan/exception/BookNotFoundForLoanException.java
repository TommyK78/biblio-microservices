package com.biblio.loan.exception;

/**
 * book-service a repondu 404 : le livre reference n'existe pas.
 * Mappee en 400 Bad Request cote loan-service : ce n'est pas l'emprunt qui est
 * introuvable, c'est la requete du client qui reference un livre inexistant.
 */
public class BookNotFoundForLoanException extends RuntimeException {

    public BookNotFoundForLoanException(Long bookId) {
        super("Le livre " + bookId + " n'existe pas");
    }
}
