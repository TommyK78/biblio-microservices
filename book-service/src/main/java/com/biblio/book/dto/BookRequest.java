package com.biblio.book.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Ce que le client envoie. Volontairement different de l'entite :
 * availableCopies n'y figure pas, c'est le serveur qui le calcule.
 */
public record BookRequest(

        @NotBlank(message = "le titre est obligatoire")
        String title,

        @NotBlank(message = "l'auteur est obligatoire")
        String author,

        @NotBlank(message = "l'ISBN est obligatoire")
        String isbn,

        @NotNull(message = "totalCopies est obligatoire")
        @Min(value = 1, message = "totalCopies doit etre superieur ou egal a 1")
        Integer totalCopies) {
}
