package com.biblio.book.repository;

import com.biblio.book.model.Book;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {

    // Methodes derivees : Spring Data genere la requete SQL a partir du nom de la methode.
    List<Book> findByAuthorContainingIgnoreCase(String author);

    List<Book> findByTitleContainingIgnoreCase(String title);

    boolean existsByIsbn(String isbn);
}
