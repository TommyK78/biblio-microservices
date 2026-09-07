package com.biblio.book.service;

import com.biblio.book.dto.BookRequest;
import com.biblio.book.dto.BookResponse;
import com.biblio.book.exception.BookNotFoundException;
import com.biblio.book.exception.DuplicateIsbnException;
import com.biblio.book.exception.NoCopyAvailableException;
import com.biblio.book.mapper.BookMapper;
import com.biblio.book.model.Book;
import com.biblio.book.repository.BookRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Transactional(readOnly = true)
    public List<BookResponse> findAll(String author, String title) {
        List<Book> books;
        if (author != null && !author.isBlank()) {
            books = bookRepository.findByAuthorContainingIgnoreCase(author);
        } else if (title != null && !title.isBlank()) {
            books = bookRepository.findByTitleContainingIgnoreCase(title);
        } else {
            books = bookRepository.findAll();
        }
        return books.stream().map(BookMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BookResponse findById(Long id) {
        return BookMapper.toResponse(getBookOrThrow(id));
    }

    @Transactional
    public BookResponse create(BookRequest request) {
        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new DuplicateIsbnException(request.isbn());
        }
        Book saved = bookRepository.save(BookMapper.toEntity(request));
        return BookMapper.toResponse(saved);
    }

    @Transactional
    public BookResponse update(Long id, BookRequest request) {
        Book book = getBookOrThrow(id);

        if (!book.getIsbn().equals(request.isbn()) && bookRepository.existsByIsbn(request.isbn())) {
            throw new DuplicateIsbnException(request.isbn());
        }

        // Nombre d'exemplaires actuellement empruntes : on le conserve lors de la mise a jour.
        int borrowed = book.getTotalCopies() - book.getAvailableCopies();

        book.setTitle(request.title());
        book.setAuthor(request.author());
        book.setIsbn(request.isbn());
        book.setTotalCopies(request.totalCopies());
        // Invariant : 0 <= availableCopies <= totalCopies
        book.setAvailableCopies(Math.max(0, request.totalCopies() - borrowed));

        return BookMapper.toResponse(bookRepository.save(book));
    }

    @Transactional
    public void delete(Long id) {
        Book book = getBookOrThrow(id);
        bookRepository.delete(book);
    }

    /**
     * Appele par loan-service lors de la creation d'un emprunt.
     * Defense en profondeur : on REVERIFIE la disponibilite ici, meme si l'appelant
     * pretend l'avoir deja fait. Entre sa verification et cet appel, un autre emprunt
     * a pu consommer le dernier exemplaire (TOCTOU).
     */
    @Transactional
    public BookResponse decrementStock(Long id) {
        Book book = getBookOrThrow(id);

        if (book.getAvailableCopies() <= 0) {
            throw new NoCopyAvailableException(id);
        }

        book.setAvailableCopies(book.getAvailableCopies() - 1);
        return BookMapper.toResponse(bookRepository.save(book));
    }

    /**
     * Appele par loan-service lors du retour d'un emprunt.
     * Le plafond a totalCopies garantit qu'un double retour ne cree pas des exemplaires
     * qui n'existent pas.
     */
    @Transactional
    public BookResponse incrementStock(Long id) {
        Book book = getBookOrThrow(id);

        book.setAvailableCopies(Math.min(book.getTotalCopies(), book.getAvailableCopies() + 1));
        return BookMapper.toResponse(bookRepository.save(book));
    }

    private Book getBookOrThrow(Long id) {
        return bookRepository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }
}
