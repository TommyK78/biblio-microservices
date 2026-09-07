package com.biblio.book.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.biblio.book.repository.BookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Test d'integration : contexte Spring complet + base H2 + couche web.
 * On verifie ici les CODES HTTP, pas la logique metier (deja couverte en unitaire).
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        bookRepository.deleteAll();
    }

    private Long createBook(String isbn, int totalCopies) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "title", "Dune",
                "author", "Frank Herbert",
                "isbn", isbn,
                "totalCopies", totalCopies));

        MvcResult result = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                // A la creation, availableCopies est initialise a totalCopies
                .andExpect(jsonPath("$.availableCopies").value(totalCopies))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    @DisplayName("POST /api/books cree le livre avec availableCopies = totalCopies")
    void createBook_shouldInitialiseAvailableCopies() throws Exception {
        Long id = createBook("978-0441013593", 2);

        mockMvc.perform(get("/api/books/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCopies").value(2))
                .andExpect(jsonPath("$.availableCopies").value(2));
    }

    @Test
    @DisplayName("PATCH decrement-stock renvoie 409 quand le stock est epuise")
    void decrementStock_shouldReturn409_whenStockExhausted() throws Exception {
        Long id = createBook("978-0000000001", 1);

        // Premier emprunt : accepte, il reste 1 exemplaire
        mockMvc.perform(patch("/api/books/" + id + "/decrement-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCopies").value(0));

        // Deuxieme : le stock est vide -> 409 Conflict, pas une erreur 500
        mockMvc.perform(patch("/api/books/" + id + "/decrement-stock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        Matchers.containsString("Aucun exemplaire disponible")));
    }

    @Test
    @DisplayName("PATCH increment-stock ne fait jamais depasser totalCopies")
    void incrementStock_shouldBeCappedAtTotalCopies() throws Exception {
        Long id = createBook("978-0000000002", 1);

        // Deux retours consecutifs alors qu'un seul exemplaire a ete emprunte
        mockMvc.perform(patch("/api/books/" + id + "/decrement-stock")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/books/" + id + "/increment-stock")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/books/" + id + "/increment-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCopies").value(1));
    }

    @Test
    @DisplayName("PATCH decrement-stock sur un livre inexistant renvoie 404")
    void decrementStock_shouldReturn404_whenBookUnknown() throws Exception {
        mockMvc.perform(patch("/api/books/9999/decrement-stock"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/books refuse un payload invalide avec 400 et le detail des champs")
    void createBook_shouldReturn400_whenPayloadInvalid() throws Exception {
        String invalid = "{\"title\":\"\",\"author\":\"X\",\"isbn\":\"1\",\"totalCopies\":0}";

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.totalCopies").exists());
    }
}
