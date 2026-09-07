package com.biblio.book.dto;

/** Ce que l'API renvoie. C'est aussi ce que loan-service deserialise via Feign. */
public record BookResponse(
        Long id,
        String title,
        String author,
        String isbn,
        Integer totalCopies,
        Integer availableCopies) {
}
