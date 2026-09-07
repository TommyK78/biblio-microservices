package com.biblio.loan.client;

import com.biblio.loan.dto.BookDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Client HTTP declaratif vers book-service.
 * "book-service" est le nom enregistre dans Eureka, pas une URL : c'est Eureka
 * qui resout l'adresse, et le load-balancer qui choisit l'instance.
 */
@FeignClient(name = "book-service")
public interface BookClient {

    @GetMapping("/api/books/{id}")
    BookDto getBookById(@PathVariable("id") Long id);

    @PatchMapping("/api/books/{id}/decrement-stock")
    BookDto decrementStock(@PathVariable("id") Long id);

    @PatchMapping("/api/books/{id}/increment-stock")
    BookDto incrementStock(@PathVariable("id") Long id);
}
