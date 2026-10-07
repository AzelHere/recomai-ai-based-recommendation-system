package com.guvi.recommendationsystem.web;

import com.guvi.recommendationsystem.exception.ForbiddenRequestException;
import com.guvi.recommendationsystem.exception.UnauthorizedRequestException;
import com.guvi.recommendationsystem.model.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SessionAccessTest {
    private final SessionAccess sessionAccess = new SessionAccess();

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

    private UserProfile profile(String role) {
        LocalDateTime now = LocalDateTime.now();
        return new UserProfile(17L, "sufiyan", "s@example.com", "Sufiyan", role, now, now);
    }
}
