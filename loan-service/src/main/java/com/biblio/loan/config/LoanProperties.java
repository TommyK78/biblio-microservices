package com.biblio.loan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Regles metier parametrables, servies par le config-server (config-repo/loan-service.yml).
 * Les valeurs par defaut permettent aux tests de tourner sans config-server.
 */
@ConfigurationProperties(prefix = "library.loan")
public record LoanProperties(
        @DefaultValue("14") int durationDays,
        @DefaultValue("3") int maxActiveLoansPerMember) {
}
