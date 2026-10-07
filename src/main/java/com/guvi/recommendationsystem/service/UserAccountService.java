package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.exception.DuplicateUserException;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import com.guvi.recommendationsystem.model.LoginRequest;
import com.guvi.recommendationsystem.model.RegisterUserRequest;
import com.guvi.recommendationsystem.model.UserAccount;
import com.guvi.recommendationsystem.model.UserProfile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UserAccountService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]{3,50}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserDao userDao;
    private final PasswordHasher passwordHasher;

    public UserAccountService(UserDao userDao, PasswordHasher passwordHasher) {
        this.userDao = userDao;
        this.passwordHasher = passwordHasher;
    }

    public UserProfile register(RegisterUserRequest request) {
        if (request == null) {
            throw new InvalidUserInputException("Registration details are required.");
        }
        String username = requiredTrimmed(request.username(), "Username");
        String email = normalizeEmail(request.email());
        String displayName = requiredTrimmed(request.displayName(), "Display name");
        String password = request.password();

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new InvalidUserInputException("Username must be 3 to 50 letters, digits, dots, underscores, or hyphens.");
        }
        if (email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidUserInputException("Enter a valid email address.");
        }
        if (displayName.length() > 100) {
            throw new InvalidUserInputException("Display name must be 100 characters or fewer.");
        }
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new InvalidUserInputException("Password must be between 8 and 128 characters.");
        }

        try {
            UserAccount user = userDao.insertUser(
                    username,
                    email,
                    passwordHasher.hash(password),
                    displayName);
            return toProfile(user);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateUserException("That username or email address is already registered.");
        }
    }

    public Optional<UserProfile> authenticate(LoginRequest request) {
        if (request == null || request.email() == null || request.password() == null) {
            return Optional.empty();
        }
        String email = normalizeEmail(request.email());
        if (email.isBlank() || email.length() > 255) {
            return Optional.empty();
        }
        return userDao.findByEmail(email)
                .filter(user -> passwordHasher.matches(request.password(), user.passwordHash()))
                .map(this::toProfile);
    }

    public UserProfile getProfile(long userId) {
        if (userId <= 0) {
            throw new InvalidUserInputException("User ID must be positive.");
        }
        return userDao.findById(userId)
                .map(this::toProfile)
                .orElseThrow(() -> new UserNotFoundException("User was not found."));
    }

    public UserProfile updateDisplayName(long userId, String displayName) {
        if (userId <= 0) {
            throw new InvalidUserInputException("User ID must be positive.");
        }
        String normalizedDisplayName = requiredTrimmed(displayName, "Display name");
        if (normalizedDisplayName.length() > 100) {
            throw new InvalidUserInputException("Display name must be 100 characters or fewer.");
        }
        if (!userDao.updateDisplayName(userId, normalizedDisplayName)) {
            throw new UserNotFoundException("User was not found.");
        }
        return getProfile(userId);
    }

    private String requiredTrimmed(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidUserInputException(label + " is required.");
        }
        return value.trim();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private UserProfile toProfile(UserAccount user) {
        return new UserProfile(
                user.userId(),
                user.username(),
                user.email(),
                user.displayName(),
                user.role(),
                user.createdAt(),
                user.updatedAt());
    }
}
