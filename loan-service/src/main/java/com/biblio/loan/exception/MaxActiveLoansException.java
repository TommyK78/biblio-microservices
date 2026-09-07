package com.biblio.loan.exception;

/** Regle bonus : un membre ne peut pas depasser N emprunts ACTIVE. Mappee en 409 Conflict. */
public class MaxActiveLoansException extends RuntimeException {

    public MaxActiveLoansException(String memberName, int max) {
        super("Le membre " + memberName + " a deja atteint la limite de " + max + " emprunts en cours");
    }
}
