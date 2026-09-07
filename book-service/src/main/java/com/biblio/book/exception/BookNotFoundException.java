package com.biblio.book.exception;

/** Le livre demande n'existe pas. Mappee en 404 Not Found. */
public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(Long id) {
        super("Livre introuvable avec l'identifiant " + id);
    }
}
