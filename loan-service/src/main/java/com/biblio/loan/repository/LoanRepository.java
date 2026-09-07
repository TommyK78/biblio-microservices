package com.biblio.loan.repository;

import com.biblio.loan.model.Loan;
import com.biblio.loan.model.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    /** Sert la regle bonus : au plus N emprunts ACTIVE par membre. */
    long countByMemberNameIgnoreCaseAndStatus(String memberName, LoanStatus status);
}
