package com.biblio.loan.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Copie locale du contrat expose par book-service.
 * loan-service ne depend PAS du code de book-service : uniquement de la forme du JSON.
 * JsonIgnoreProperties : si book-service ajoute un champ, loan-service ne casse pas.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookDto(
        Long id,
        String title,
        String author,
        String isbn,
        Integer totalCopies,
        Integer availableCopies) {
}
