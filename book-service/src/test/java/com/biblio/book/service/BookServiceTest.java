package com.biblio.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.biblio.book.dto.BookResponse;
import com.biblio.book.exception.BookNotFoundException;
import com.biblio.book.exception.NoCopyAvailableException;
import com.biblio.book.model.Book;
import com.biblio.book.repository.BookRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests unitaires de la couche service : le depot est mocke, aucune base ni serveur web.
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookService bookService;

    private Book book(Long id, int total, int available) {
        Book book = new Book("Dune", "Frank Herbert", "978-0441013593", total, available);
        book.setId(id);
        return book;
    }

    @Test
    @DisplayName("decrementStock leve NoCopyAvailableException quand availableCopies vaut 0")
    void decrementStock_shouldRefuse_whenNoCopyLeft() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book(1L, 3, 0)));

        assertThatThrownBy(() -> bookService.decrementStock(1L))
                .isInstanceOf(NoCopyAvailableException.class)
                .hasMessageContaining("Aucun exemplaire disponible");

        // Le stock ne doit surtout pas avoir ete enregistre a -1
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    @DisplayName("decrementStock retire un exemplaire quand il en reste")
    void decrementStock_shouldDecrement_whenCopyAvailable() {
        Book stored = book(1L, 3, 2);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(stored));
        when(bookRepository.save(any(Book.class))).thenAnswer(call -> call.getArgument(0));

        BookResponse response = bookService.decrementStock(1L);

        assertThat(response.availableCopies()).isEqualTo(1);
        assertThat(response.totalCopies()).isEqualTo(3);
    }

    @Test
    @DisplayName("incrementStock ne depasse jamais totalCopies")
    void incrementStock_shouldNeverExceedTotalCopies() {
        Book stored = book(1L, 2, 2);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(stored));
        when(bookRepository.save(any(Book.class))).thenAnswer(call -> call.getArgument(0));

        BookResponse response = bookService.incrementStock(1L);

        assertThat(response.availableCopies()).isEqualTo(2);
    }

    @Test
    @DisplayName("incrementStock remet un exemplaire en rayon")
    void incrementStock_shouldIncrement() {
        Book stored = book(1L, 2, 0);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(stored));
        when(bookRepository.save(any(Book.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(bookService.incrementStock(1L).availableCopies()).isEqualTo(1);
    }

    @Test
    @DisplayName("un livre inexistant leve BookNotFoundException")
    void findById_shouldThrow_whenUnknownId() {
        when(bookRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.findById(42L))
                .isInstanceOf(BookNotFoundException.class);
    }
}
