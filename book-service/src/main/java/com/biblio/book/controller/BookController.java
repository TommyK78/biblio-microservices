package com.biblio.book.controller;

import com.biblio.book.dto.BookRequest;
import com.biblio.book.dto.BookResponse;
import com.biblio.book.service.BookService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public List<BookResponse> findAll(@RequestParam(required = false) String author,
                                      @RequestParam(required = false) String title) {
        return bookService.findAll(author, title);
    }

    @GetMapping("/{id}")
    public BookResponse findById(@PathVariable Long id) {
        return bookService.findById(id);
    }

    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        BookResponse created = bookService.create(request);
        return ResponseEntity.created(URI.create("/api/books/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public BookResponse update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return bookService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bookService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Endpoint interne, appele par loan-service. 409 si plus aucun exemplaire disponible. */
    @PatchMapping("/{id}/decrement-stock")
    public BookResponse decrementStock(@PathVariable Long id) {
        return bookService.decrementStock(id);
    }

    /** Endpoint interne, appele par loan-service lors d'un retour. */
    @PatchMapping("/{id}/increment-stock")
    public BookResponse incrementStock(@PathVariable Long id) {
        return bookService.incrementStock(id);
    }
}
