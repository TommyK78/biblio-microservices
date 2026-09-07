package com.biblio.book.mapper;

import com.biblio.book.dto.BookRequest;
import com.biblio.book.dto.BookResponse;
import com.biblio.book.model.Book;

public final class BookMapper {

    private BookMapper() {
        // classe utilitaire
    }

    /** A la creation, tous les exemplaires sont disponibles. */
    public static Book toEntity(BookRequest request) {
        return new Book(
                request.title(),
                request.author(),
                request.isbn(),
                request.totalCopies(),
                request.totalCopies());
    }

    public static BookResponse toResponse(Book book) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn(),
                book.getTotalCopies(),
                book.getAvailableCopies());
    }
}
