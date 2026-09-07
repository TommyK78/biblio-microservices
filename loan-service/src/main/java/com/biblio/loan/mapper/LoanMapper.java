package com.biblio.loan.mapper;

import com.biblio.loan.dto.LoanResponse;
import com.biblio.loan.model.Loan;

public final class LoanMapper {

    private LoanMapper() {
        // classe utilitaire
    }

    public static LoanResponse toResponse(Loan loan) {
        return new LoanResponse(
                loan.getId(),
                loan.getMemberName(),
                loan.getBookId(),
                loan.getBookTitle(),
                loan.getLoanDate(),
                loan.getDueDate(),
                loan.getReturnDate(),
                loan.getStatus());
    }
}
