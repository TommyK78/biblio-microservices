package com.biblio.loan.service;

import com.biblio.loan.client.BookClient;
import com.biblio.loan.config.LoanProperties;
import com.biblio.loan.dto.BookDto;
import com.biblio.loan.dto.LoanRequest;
import com.biblio.loan.dto.LoanResponse;
import com.biblio.loan.exception.BookNotFoundForLoanException;
import com.biblio.loan.exception.BookServiceUnavailableException;
import com.biblio.loan.exception.LoanAlreadyReturnedException;
import com.biblio.loan.exception.LoanNotFoundException;
import com.biblio.loan.exception.MaxActiveLoansException;
import com.biblio.loan.exception.NoCopyAvailableException;
import com.biblio.loan.mapper.LoanMapper;
import com.biblio.loan.model.Loan;
import com.biblio.loan.model.LoanStatus;
import com.biblio.loan.repository.LoanRepository;
import feign.FeignException;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    private final LoanRepository loanRepository;
    private final BookClient bookClient;
    private final LoanProperties properties;

    public LoanService(LoanRepository loanRepository, BookClient bookClient, LoanProperties properties) {
        this.loanRepository = loanRepository;
        this.bookClient = bookClient;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> findAll() {
        return loanRepository.findAll().stream().map(LoanMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LoanResponse findById(Long id) {
        return LoanMapper.toResponse(getLoanOrThrow(id));
    }

    /**
     * Creation d'un emprunt.
     *
     * 1. Lire le livre chez book-service      -> 400 s'il n'existe pas
     * 2. Verifier la disponibilite localement -> 409 sans appeler decrement-stock
     * 3. Decrementer le stock chez book-service -> 409 si book-service refuse (concurrence)
     * 4. Seulement alors, enregistrer l'emprunt
     *
     * L'etape 2 evite un aller-retour inutile ; l'etape 3 est celle qui fait autorite,
     * parce que l'etat a pu changer entre 1 et 3 (TOCTOU).
     */
    @Transactional
    public LoanResponse create(LoanRequest request) {
        BookDto book = fetchBook(request.bookId());

        // Etape 2 : verification prealable. On NE DOIT PAS appeler decrement-stock dans ce cas.
        if (book.availableCopies() == null || book.availableCopies() <= 0) {
            throw new NoCopyAvailableException(request.bookId());
        }

        long activeLoans = loanRepository.countByMemberNameIgnoreCaseAndStatus(
                request.memberName(), LoanStatus.ACTIVE);
        if (activeLoans >= properties.maxActiveLoansPerMember()) {
            throw new MaxActiveLoansException(request.memberName(), properties.maxActiveLoansPerMember());
        }

        // Etape 3 : c'est book-service qui tranche reellement.
        decrementStock(request.bookId());

        LocalDate today = LocalDate.now();
        Loan loan = new Loan(
                request.memberName(),
                request.bookId(),
                book.title(),
                today,
                today.plusDays(properties.durationDays()));

        return LoanMapper.toResponse(loanRepository.save(loan));
    }

    /**
     * Retour d'un emprunt : verifier qu'il est encore ACTIVE, reincrementer le stock,
     * puis marquer l'emprunt comme rendu.
     */
    @Transactional
    public LoanResponse returnLoan(Long id) {
        Loan loan = getLoanOrThrow(id);

        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new LoanAlreadyReturnedException(id);
        }

        incrementStock(loan.getBookId());

        loan.setStatus(LoanStatus.RETURNED);
        loan.setReturnDate(LocalDate.now());
        return LoanMapper.toResponse(loanRepository.save(loan));
    }

    private BookDto fetchBook(Long bookId) {
        try {
            return bookClient.getBookById(bookId);
        } catch (FeignException.NotFound ex) {
            throw new BookNotFoundForLoanException(bookId);
        } catch (FeignException ex) {
            throw new BookServiceUnavailableException(ex);
        }
    }

    private void decrementStock(Long bookId) {
        try {
            bookClient.decrementStock(bookId);
        } catch (FeignException.Conflict ex) {
            // Cas de concurrence : le stock etait > 0 a l'etape 1, il ne l'est plus.
            throw new NoCopyAvailableException(bookId);
        } catch (FeignException.NotFound ex) {
            throw new BookNotFoundForLoanException(bookId);
        } catch (FeignException ex) {
            throw new BookServiceUnavailableException(ex);
        }
    }

    private void incrementStock(Long bookId) {
        try {
            bookClient.incrementStock(bookId);
        } catch (FeignException.NotFound ex) {
            // Le livre a ete supprime entre-temps : on refuse quand meme pas le retour,
            // sinon l'emprunt resterait ACTIVE pour toujours.
            log.warn("Retour de l'emprunt : le livre {} n'existe plus chez book-service", bookId);
        } catch (FeignException ex) {
            throw new BookServiceUnavailableException(ex);
        }
    }

    private Loan getLoanOrThrow(Long id) {
        return loanRepository.findById(id).orElseThrow(() -> new LoanNotFoundException(id));
    }
}
