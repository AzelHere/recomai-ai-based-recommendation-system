package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.PasswordResetDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.model.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {
    private final PasswordResetDao resetDao = mock(PasswordResetDao.class);
    private final UserDao userDao = mock(UserDao.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final ResetMailDelivery mailDelivery = mock(ResetMailDelivery.class);
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(resetDao, userDao, passwordHasher, mailDelivery,
                new NoOpTransactionManager(), "http://localhost:8080", 60,
                new org.springframework.mock.env.MockEnvironment());
    }

    @Test
    void createsAHashOnlyRecordForKnownNormalizedAddress() {
        when(resetDao.findUserIdByEmailForUpdate("person@example.test")).thenReturn(OptionalLong.of(17));
        when(resetDao.hasRecentResetRequest(17, 60)).thenReturn(false);
        when(userDao.findById(17)).thenReturn(Optional.of(user()));

        service.requestReset(service.normalizeAndValidateEmail(" Person@Example.Test "), "request-1");

        var hash = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(resetDao).insertToken(eq(17L), hash.capture());
        assertThat(hash.getValue()).hasSize(32);
        verify(mailDelivery).deliver(eq("person@example.test"), eq(hash.getValue()), anyString(), eq("request-1"));
    }

    @Test
    void unknownAddressDoesNotIssueOrDeliverAToken() {
        when(resetDao.findUserIdByEmailForUpdate("unknown@example.test")).thenReturn(OptionalLong.empty());

        service.requestReset(service.normalizeAndValidateEmail("unknown@example.test"), "request-2");

        verify(resetDao, never()).insertToken(anyLong(), any());
        verify(mailDelivery, never()).deliver(anyString(), any(), anyString(), anyString());
    }

    @Test
    void normalizesValidEmailsAndRejectsMalformedInputBeforeQueueing() {
        assertThat(service.normalizeAndValidateEmail(" Person@Example.Test ")).isEqualTo("person@example.test");
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> service.normalizeAndValidateEmail("not-an-email"))
                .withMessage("Enter a valid email address.");
    }

    @Test
    void rejectsMismatchedOrWeakPasswordsBeforeTokenConsumption() {
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> service.completeReset("A".repeat(43), "password-one", "password-two"))
                .withMessage("The passwords do not match.");
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> service.completeReset("A".repeat(43), "short", "short"))
                .withMessage("Password must be between 8 and 128 characters.");
        verify(resetDao, never()).consumeTokenAndUpdatePassword(any(), anyString());
    }

    @Test
    void hashesSuppliedTokenAndPasswordBeforeAtomicConsume() {
        String token = "A".repeat(43);
        assertThat(token).hasSize(43);
        when(passwordHasher.hash("New-password-123!")).thenReturn("pbkdf2-test-hash");
        when(resetDao.consumeTokenAndUpdatePassword(any(), eq("pbkdf2-test-hash"))).thenReturn(true);

        service.completeReset(token, "New-password-123!", "New-password-123!");

        var hash = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(resetDao).consumeTokenAndUpdatePassword(hash.capture(), eq("pbkdf2-test-hash"));
        assertThat(hash.getValue()).hasSize(32);
        assertThat(new String(hash.getValue(), java.nio.charset.StandardCharsets.US_ASCII)).isNotEqualTo(token);
    }

    @Test
    void reportsReplayExpiredAndInvalidTokensWithoutUserDetails() {
        String token = "A".repeat(43);
        when(passwordHasher.hash("New-password-123!")).thenReturn("pbkdf2-test-hash");
        when(resetDao.consumeTokenAndUpdatePassword(any(), anyString())).thenReturn(false);

        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> service.completeReset(token, "New-password-123!", "New-password-123!"))
                .withMessage(PasswordResetService.INVALID_TOKEN_MESSAGE);
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> service.completeReset("short", "New-password-123!", "New-password-123!"))
                .withMessage(PasswordResetService.INVALID_TOKEN_MESSAGE);
    }

    @Test
    void enforcesHttpsTrustedBaseUrlInProduction() {
        var productionEnvironment = new org.springframework.mock.env.MockEnvironment();
        productionEnvironment.setActiveProfiles("prod");
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> new PasswordResetService(resetDao, userDao, passwordHasher, mailDelivery,
                        new NoOpTransactionManager(), "http://untrusted.example", 60, productionEnvironment))
                .withMessage("APP_PUBLIC_URL must use HTTPS in production.");
    }

    private UserAccount user() {
        LocalDateTime now = LocalDateTime.now();
        return new UserAccount(17, "person", "person@example.test", "never-exposed", "Person", "USER", now, now);
    }

    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) { }
        @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) { }
    }
}
