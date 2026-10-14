package com.guvi.recommendationsystem.web;

import com.guvi.recommendationsystem.exception.ForbiddenRequestException;
import com.guvi.recommendationsystem.exception.UnauthorizedRequestException;
import com.guvi.recommendationsystem.model.UserProfile;
import com.guvi.recommendationsystem.dao.UserDao;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SessionAccessTest {
    private final UserDao userDao = org.mockito.Mockito.mock(UserDao.class);
    private final SessionAccess sessionAccess = new SessionAccess(userDao);

    SessionAccessTest() {
        org.mockito.Mockito.when(userDao.findAuthVersion(17L)).thenReturn(java.util.OptionalInt.of(1));
    }

    @Test
    void sessionStoresTheAuthenticatedUserAndAdminRole() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        sessionAccess.startSession(request, profile("ADMIN"));

        assertThat(sessionAccess.requireUserId(request)).isEqualTo(17L);
        assertThat(sessionAccess.requireAdmin(request)).isEqualTo(17L);
    }

    @Test
    void ordinaryUsersCannotUseAdminActions() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        sessionAccess.startSession(request, profile("USER"));

        assertThatExceptionOfType(ForbiddenRequestException.class)
                .isThrownBy(() -> sessionAccess.requireAdmin(request));
    }

    @Test
    void anonymousRequestsAreRejected() {
        assertThatExceptionOfType(UnauthorizedRequestException.class)
                .isThrownBy(() -> sessionAccess.requireUserId(new MockHttpServletRequest()));
    }

    @Test
    void passwordResetVersionRevokesAnExistingSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        sessionAccess.startSession(request, profile("USER"));
        org.mockito.Mockito.when(userDao.findAuthVersion(17L)).thenReturn(java.util.OptionalInt.of(2));
        assertThatExceptionOfType(UnauthorizedRequestException.class)
                .isThrownBy(() -> sessionAccess.requireUserId(request));
        assertThat(request.getSession(false)).isNull();
    }

    private UserProfile profile(String role) {
        LocalDateTime now = LocalDateTime.now();
        return new UserProfile(17L, "sufiyan", "s@example.com", "Sufiyan", role, now, now);
    }
}
