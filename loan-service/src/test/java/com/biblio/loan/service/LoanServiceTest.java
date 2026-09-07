package com.biblio.loan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.biblio.loan.client.BookClient;
import com.biblio.loan.config.LoanProperties;
import com.biblio.loan.dto.BookDto;
import com.biblio.loan.dto.LoanRequest;
import com.biblio.loan.dto.LoanResponse;
import com.biblio.loan.exception.BookNotFoundForLoanException;
import com.biblio.loan.exception.LoanAlreadyReturnedException;
import com.biblio.loan.exception.MaxActiveLoansException;
import com.biblio.loan.exception.NoCopyAvailableException;
import com.biblio.loan.model.Loan;
import com.biblio.loan.model.LoanStatus;
import com.biblio.loan.repository.LoanRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests unitaires de la regle metier. Le client Feign est mocke :
 * aucun appel reseau, aucun book-service necessaire.
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private BookClient bookClient;

    private LoanService loanService;

    @BeforeEach
    void setUp() {
        loanService = new LoanService(loanRepository, bookClient, new LoanProperties(14, 3));
    }

    /** Fabrique une FeignException du bon sous-type a partir d'un code HTTP. */
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

    private BookDto book(int availableCopies) {
        return new BookDto(1L, "Dune", "Frank Herbert", "978-0441013593", 3, availableCopies);
    }

    @Test
    @DisplayName("Stock epuise : 409 et decrement-stock n'est JAMAIS appele")
    void create_shouldRefuse_andNotCallDecrement_whenNoCopyAvailable() {
        when(bookClient.getBookById(1L)).thenReturn(book(0));

        assertThatThrownBy(() -> loanService.create(new LoanRequest("Bob", 1L)))
                .isInstanceOf(NoCopyAvailableException.class);

        // Le point cle de l'exercice : on n'appelle pas book-service pour rien.
        verify(bookClient, never()).decrementStock(anyLong());
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    @DisplayName("Cas de concurrence : book-service repond 409 au decrement, l'emprunt n'est pas cree")
    void create_shouldPropagateConflict_whenBookServiceRefusesDecrement() {
        // Etape 1 : le livre semble disponible...
        when(bookClient.getBookById(1L)).thenReturn(book(1));
        when(loanRepository.countByMemberNameIgnoreCaseAndStatus(anyString(), any())).thenReturn(0L);
        // ... mais entre-temps un autre emprunt a pris le dernier exemplaire (TOCTOU)
        when(bookClient.decrementStock(1L)).thenThrow(feignError(409));

        assertThatThrownBy(() -> loanService.create(new LoanRequest("Bob", 1L)))
                .isInstanceOf(NoCopyAvailableException.class);

        // Aucun emprunt fantome ne doit rester en base
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    @DisplayName("Livre inexistant : BookNotFoundForLoanException (mappee en 400)")
    void create_shouldFail_whenBookDoesNotExist() {
        when(bookClient.getBookById(99L)).thenThrow(feignError(404));

        assertThatThrownBy(() -> loanService.create(new LoanRequest("Bob", 99L)))
                .isInstanceOf(BookNotFoundForLoanException.class);

        verify(bookClient, never()).decrementStock(anyLong());
    }

    @Test
    @DisplayName("Cas nominal : emprunt ACTIVE, dueDate a +14 jours, titre copie en snapshot")
    void create_shouldSucceed_whenCopyAvailable() {
        when(bookClient.getBookById(1L)).thenReturn(book(2));
        when(loanRepository.countByMemberNameIgnoreCaseAndStatus(anyString(), any())).thenReturn(0L);
        when(bookClient.decrementStock(1L)).thenReturn(book(1));
        when(loanRepository.save(any(Loan.class))).thenAnswer(call -> call.getArgument(0));

        LoanResponse response = loanService.create(new LoanRequest("Bob", 1L));

        assertThat(response.status()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(response.bookTitle()).isEqualTo("Dune");
        assertThat(response.returnDate()).isNull();
        assertThat(response.dueDate()).isEqualTo(response.loanDate().plusDays(14));
        verify(bookClient).decrementStock(1L);
    }

    @Test
    @DisplayName("Plus de 3 emprunts en cours : 409 et pas de decrement")
    void create_shouldRefuse_whenMemberReachedMaxActiveLoans() {
        when(bookClient.getBookById(1L)).thenReturn(book(5));
        when(loanRepository.countByMemberNameIgnoreCaseAndStatus("Bob", LoanStatus.ACTIVE)).thenReturn(3L);

        assertThatThrownBy(() -> loanService.create(new LoanRequest("Bob", 1L)))
                .isInstanceOf(MaxActiveLoansException.class);

        verify(bookClient, never()).decrementStock(anyLong());
    }

    @Test
    @DisplayName("Retour d'un emprunt deja rendu : 409 et pas de reincrementation")
    void returnLoan_shouldRefuse_whenAlreadyReturned() {
        Loan loan = new Loan("Bob", 1L, "Dune", LocalDate.now(), LocalDate.now().plusDays(14));
        loan.setId(7L);
        loan.setStatus(LoanStatus.RETURNED);
        loan.setReturnDate(LocalDate.now());
        when(loanRepository.findById(7L)).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.returnLoan(7L))
                .isInstanceOf(LoanAlreadyReturnedException.class);

        // Sinon un double retour gonflerait le stock du livre
        verify(bookClient, never()).incrementStock(anyLong());
    }

    @Test
    @DisplayName("Retour nominal : increment-stock appele, statut RETURNED, returnDate remplie")
    void returnLoan_shouldIncrementStockAndMarkReturned() {
        Loan loan = new Loan("Bob", 1L, "Dune", LocalDate.now(), LocalDate.now().plusDays(14));
        loan.setId(7L);
        when(loanRepository.findById(7L)).thenReturn(Optional.of(loan));
        when(bookClient.incrementStock(1L)).thenReturn(book(3));
        when(loanRepository.save(any(Loan.class))).thenAnswer(call -> call.getArgument(0));

        LoanResponse response = loanService.returnLoan(7L);

        assertThat(response.status()).isEqualTo(LoanStatus.RETURNED);
        assertThat(response.returnDate()).isEqualTo(LocalDate.now());
        verify(bookClient).incrementStock(1L);
    }
}
