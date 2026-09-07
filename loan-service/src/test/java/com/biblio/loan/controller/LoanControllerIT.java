package com.biblio.loan.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.biblio.loan.client.BookClient;
import com.biblio.loan.dto.BookDto;
import com.biblio.loan.repository.LoanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Test d'integration de loan-service : contexte Spring complet + H2,
 * mais BookClient est remplace par un mock -> book-service n'a pas besoin de tourner.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoanControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BookClient bookClient;

    @BeforeEach
    void cleanDatabase() {
        loanRepository.deleteAll();
    }

    private BookDto book(int availableCopies) {
        return new BookDto(1L, "Dune", "Frank Herbert", "978-0441013593", 3, availableCopies);
    }

    private FeignException feignError(int status) {
        Request request = Request.create(
                Request.HttpMethod.PATCH,
                "/api/books/1/decrement-stock",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                new RequestTemplate());

        Response response = Response.builder()
                .status(status)
                .reason("erreur simulee")
                .request(request)
                .headers(Collections.emptyMap())
                .build();

        return FeignException.errorStatus("BookClient#decrementStock(Long)", response);
    }

    private String loanPayload(String memberName, long bookId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("memberName", memberName, "bookId", bookId));
    }

    @Test
    @DisplayName("POST /api/loans renvoie 201 et un emprunt ACTIVE")
    void createLoan_shouldReturn201() throws Exception {
        when(bookClient.getBookById(1L)).thenReturn(book(2));
        when(bookClient.decrementStock(1L)).thenReturn(book(1));

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loanPayload("Bob", 1L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.bookTitle").value("Dune"))
                .andExpect(jsonPath("$.returnDate").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/loans renvoie 409 quand le stock est epuise")
    void createLoan_shouldReturn409_whenStockExhausted() throws Exception {
        when(bookClient.getBookById(1L)).thenReturn(book(0));

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loanPayload("Bob", 1L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        verify(bookClient, never()).decrementStock(anyLong());
    }

    @Test
    @DisplayName("POST /api/loans renvoie 409 quand book-service refuse le decrement (concurrence)")
    void createLoan_shouldReturn409_whenBookServiceRefusesDecrement() throws Exception {
        when(bookClient.getBookById(1L)).thenReturn(book(1));
        when(bookClient.decrementStock(1L)).thenThrow(feignError(409));

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loanPayload("Bob", 1L)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/loans renvoie 400 quand le livre n'existe pas")
    void createLoan_shouldReturn400_whenBookDoesNotExist() throws Exception {
        when(bookClient.getBookById(99L)).thenThrow(feignError(404));

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loanPayload("Bob", 99L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Rendre deux fois le meme emprunt : 200 puis 409")
    void returnLoan_shouldReturn409_onSecondAttempt() throws Exception {
        when(bookClient.getBookById(1L)).thenReturn(book(2));
        when(bookClient.decrementStock(1L)).thenReturn(book(1));
        when(bookClient.incrementStock(1L)).thenReturn(book(2));

        MvcResult created = mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loanPayload("Bob", 1L)))
                .andExpect(status().isCreated())
                .andReturn();

        long loanId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/loans/" + loanId + "/return"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.returnDate").exists());

        mockMvc.perform(patch("/api/loans/" + loanId + "/return"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("PATCH return sur un emprunt inexistant renvoie 404")
    void returnLoan_shouldReturn404_whenLoanUnknown() throws Exception {
        mockMvc.perform(patch("/api/loans/9999/return"))
                .andExpect(status().isNotFound());
    }
}
