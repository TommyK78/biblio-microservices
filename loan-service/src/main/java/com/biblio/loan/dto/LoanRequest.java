package com.biblio.loan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoanRequest(

        @NotBlank(message = "memberName est obligatoire")
        String memberName,

        @NotNull(message = "bookId est obligatoire")
        Long bookId) {
}
