package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.exception.DuplicateUserException;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.model.LoginRequest;
import com.guvi.recommendationsystem.model.RegisterUserRequest;
import com.guvi.recommendationsystem.model.UserAccount;
import com.guvi.recommendationsystem.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {
    @Mock
    private UserDao userDao;
    @Mock
    private PasswordHasher passwordHasher;

    private UserAccountService service;

    @BeforeEach
    void setUp() {
        service = new UserAccountService(userDao, passwordHasher);
    }

    @Test
    void registrationNormalizesEmailAndStoresOnlyThePasswordHash() {
        when(passwordHasher.hash("strong-pass-9")).thenReturn("encoded-hash");
        when(userDao.insertUser("sufiyan", "sufiyan@example.com", "encoded-hash", "Sufiyan"))
                .thenReturn(account("sufiyan@example.com"));

        UserProfile profile = service.register(new RegisterUserRequest(
                "sufiyan", " Sufiyan@Example.com ", "strong-pass-9", " Sufiyan "));

        assertThat(profile.email()).isEqualTo("sufiyan@example.com");
        assertThat(profile.displayName()).isEqualTo("Sufiyan");
        verify(userDao).insertUser("sufiyan", "sufiyan@example.com", "encoded-hash", "Sufiyan");
    }

    @Test
    void registrationRejectsShortPasswordsBeforeWritingToTheDatabase() {
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> service.register(new RegisterUserRequest(
                        "sufiyan", "sufiyan@example.com", "short", "Sufiyan")));

        verifyNoInteractions(userDao, passwordHasher);
    }

    @Test
    void registrationMapsDatabaseUniquenessErrorsToADomainConflict() {
        when(passwordHasher.hash("strong-pass-9")).thenReturn("encoded-hash");
        when(userDao.insertUser("sufiyan", "sufiyan@example.com", "encoded-hash", "Sufiyan"))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatExceptionOfType(DuplicateUserException.class)
                .isThrownBy(() -> service.register(new RegisterUserRequest(
                        "sufiyan", "sufiyan@example.com", "strong-pass-9", "Sufiyan")));
    }

    @Test
    void authenticateReturnsNoProfileForAnIncorrectPassword() {
        when(userDao.findByEmail("sufiyan@example.com")).thenReturn(Optional.of(account("sufiyan@example.com")));
        when(passwordHasher.matches("wrong-password", "saved-hash")).thenReturn(false);

        assertThat(service.authenticate(new LoginRequest("Sufiyan@Example.com", "wrong-password")))
                .isEmpty();
    }

    private UserAccount account(String email) {
        LocalDateTime now = LocalDateTime.now();
        return new UserAccount(9L, "sufiyan", email, "saved-hash", "Sufiyan", "USER", now, now);
    }
}
