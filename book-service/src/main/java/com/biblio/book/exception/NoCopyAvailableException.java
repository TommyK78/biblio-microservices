package com.biblio.book.exception;

/**
 * Tentative de decrementer le stock d'un livre dont availableCopies vaut deja 0.
 * Mappee en 409 Conflict : la requete est syntaxiquement valide, mais elle entre
 * en conflit avec l'etat courant de la ressource.
 */
public class NoCopyAvailableException extends RuntimeException {

    public NoCopyAvailableException(Long bookId) {
        super("Aucun exemplaire disponible pour le livre " + bookId);
    }
}
