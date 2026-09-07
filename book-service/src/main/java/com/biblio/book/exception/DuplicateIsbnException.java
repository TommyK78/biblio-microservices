package com.biblio.book.exception;

/** Un livre avec cet ISBN existe deja. Mappee en 409 Conflict. */
public class DuplicateIsbnException extends RuntimeException {

    public DuplicateIsbnException(String isbn) {
        super("Un livre existe deja avec l'ISBN " + isbn);
    }
}
