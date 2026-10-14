package com.guvi.recommendationsystem.web;

import com.guvi.recommendationsystem.exception.ForbiddenRequestException;
import com.guvi.recommendationsystem.exception.UnauthorizedRequestException;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.model.UserProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

@Component
public class SessionAccess {
    private static final String USER_ID_ATTRIBUTE = "authenticatedUserId";
    private static final String USER_ROLE_ATTRIBUTE = "authenticatedUserRole";
    private static final String AUTH_VERSION_ATTRIBUTE = "authenticatedUserVersion";

    private final UserDao userDao;

    public SessionAccess(UserDao userDao) { this.userDao = userDao; }

    public void startSession(HttpServletRequest request, UserProfile profile) {
        HttpSession previousSession = request.getSession(false);
        if (previousSession != null) {
            previousSession.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(USER_ID_ATTRIBUTE, profile.userId());
        session.setAttribute(USER_ROLE_ATTRIBUTE, profile.role());
        session.setAttribute(AUTH_VERSION_ATTRIBUTE, userDao.findAuthVersion(profile.userId()).orElse(1));
    }

    public long requireUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new UnauthorizedRequestException("Sign in to access this resource.");
        }
        Object userId = session.getAttribute(USER_ID_ATTRIBUTE);
        if (!(userId instanceof Long authenticatedUserId)) {
            throw new UnauthorizedRequestException("Sign in to access this resource.");
        }
        Object sessionVersion = session.getAttribute(AUTH_VERSION_ATTRIBUTE);
        java.util.OptionalInt dbVersion = userDao.findAuthVersion(authenticatedUserId);
        int currentVersion = dbVersion == null ? -1 : dbVersion.orElse(-1);
        int version = sessionVersion instanceof Integer savedVersion ? savedVersion : 1;
        if (currentVersion < 0 || version != currentVersion) {
            session.invalidate();
            throw new UnauthorizedRequestException("Sign in to access this resource.");
        }
        return authenticatedUserId;
    }

    public long requireAdmin(HttpServletRequest request) {
        long userId = requireUserId(request);
        HttpSession session = request.getSession(false);
        Object role = session == null ? null : session.getAttribute(USER_ROLE_ATTRIBUTE);
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenRequestException("Administrator access is required.");
        }
        return userId;
    }

    public void endSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
