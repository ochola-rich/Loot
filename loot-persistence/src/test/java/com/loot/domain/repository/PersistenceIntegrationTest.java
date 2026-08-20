package com.loot.domain.repository;

import com.loot.crypto.PhoneNumberConverter;
import com.loot.domain.model.EntryPayment;
import com.loot.domain.model.GatewayTransaction;
import com.loot.domain.model.Tournament;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PhoneNumberConverter.class)
@Testcontainers
class PersistenceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // @DataJpaTest doesn't component-scan @Component converters by default,
        // hence the explicit @Import above - it still needs the key property
        // PhoneNumberConverter's constructor requires.
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.phone-key", () -> Base64.getEncoder().encodeToString(key));
    }

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private GatewayTransactionRepository gatewayTransactionRepository;

    @Test
    void savesAndReloadsATournamentWithAuditTimestamps() {
        Tournament saved = tournamentRepository.save(newTournament("Friday Night FIFA", 64));

        assertThat(saved.getId()).isPositive();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void createsAPaymentForATournamentAndQueriesByStatus() {
        Tournament tournament = tournamentRepository.save(newTournament("Sunday League", 32));

        EntryPayment payment = new EntryPayment();
        payment.setTournamentId(tournament.getId());
        payment.setPlayerPhone("+254712345678");
        payment.setAmountKes(BigDecimal.valueOf(100));
        payment.setGateway("MPESA");
        payment.setStatus("PENDING");
        paymentRepository.save(payment);

        List<EntryPayment> pending =
                paymentRepository.findByTournamentIdAndStatus(tournament.getId(), "PENDING");

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getPlayerPhone()).isEqualTo("+254712345678");
    }

    @Test
    void rejectsDuplicateIdempotencyKeys() {
        gatewayTransactionRepository.saveAndFlush(newGatewayTransaction("dup-key"));

        assertThrows(DataIntegrityViolationException.class, () ->
                gatewayTransactionRepository.saveAndFlush(newGatewayTransaction("dup-key")));
    }

    private Tournament newTournament(String name, int maxEntries) {
        Tournament tournament = new Tournament();
        tournament.setName(name);
        tournament.setEntryFeeKes(BigDecimal.valueOf(50));
        tournament.setMaxEntries(maxEntries);
        tournament.setStatus("OPEN");
        return tournament;
    }

    private GatewayTransaction newGatewayTransaction(String idempotencyKey) {
        GatewayTransaction tx = new GatewayTransaction();
        tx.setIdempotencyKey(idempotencyKey);
        tx.setGateway("MPESA");
        return tx;
    }
}
