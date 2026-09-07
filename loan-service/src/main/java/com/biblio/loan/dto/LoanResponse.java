package com.biblio.loan.dto;

import com.biblio.loan.model.LoanStatus;
import java.time.LocalDate;

public record LoanResponse(
        Long id,
        String memberName,
        Long bookId,
        String bookTitle,
        LocalDate loanDate,
        LocalDate dueDate,
        LocalDate returnDate,
        LoanStatus status) {
}
