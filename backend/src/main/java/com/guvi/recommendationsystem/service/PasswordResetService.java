package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.PasswordResetDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Pattern;

@Service
public class PasswordResetService {
    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    public static final String REQUEST_MESSAGE =
            "If an account exists for this email address, password reset instructions will be sent.";
    public static final String INVALID_TOKEN_MESSAGE =
            "This reset link is invalid or expired. Request a new password reset link.";

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final PasswordResetDao resetDao;
    private final UserDao userDao;
    private final PasswordHasher passwordHasher;
    private final ResetMailDelivery mailDelivery;
    private final TransactionTemplate transaction;
    private final SecureRandom random = new SecureRandom();
    private final String trustedPublicUrl;
    private final int accountCooldownSeconds;

    public PasswordResetService(PasswordResetDao resetDao, UserDao userDao, PasswordHasher passwordHasher,
                                ResetMailDelivery mailDelivery, PlatformTransactionManager transactionManager,
                                @Value("${app.public-url:http://localhost:8080}") String trustedPublicUrl,
                                @Value("${app.password-reset.account-cooldown-seconds:60}") int accountCooldownSeconds,
                                Environment environment) {
        this.resetDao = resetDao;
        this.userDao = userDao;
        this.passwordHasher = passwordHasher;
        this.mailDelivery = mailDelivery;
        this.transaction = new TransactionTemplate(transactionManager);
        this.trustedPublicUrl = trustedPublicUrl.replaceAll("/+$", "");
        this.accountCooldownSeconds = Math.max(0, accountCooldownSeconds);
        boolean production = java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        java.net.URI baseUri;
        try { baseUri = java.net.URI.create(this.trustedPublicUrl); }
        catch (IllegalArgumentException invalidUrl) { throw new IllegalStateException("APP_PUBLIC_URL is invalid."); }
        if (baseUri.getHost() == null || (production && !"https".equalsIgnoreCase(baseUri.getScheme()))) {
            throw new IllegalStateException("APP_PUBLIC_URL must use HTTPS in production.");
        }
    }

    public String normalizeAndValidateEmail(String suppliedEmail) {
        String email = suppliedEmail == null ? "" : suppliedEmail.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new InvalidUserInputException("Enter a valid email address.");
        }
        return email;
    }

    @Async("applicationTaskExecutor")
    public void requestReset(String suppliedEmail, String requestId) {
        try {
            processResetRequest(suppliedEmail, requestId);
        } catch (RuntimeException failure) {
            logger.warn("Password reset request processing failed requestId={} reason={}",
                    requestId, failure.getClass().getSimpleName());
        }
    }

    private void processResetRequest(String suppliedEmail, String requestId) {
        String email = suppliedEmail;
        byte[] randomToken = new byte[32];
        random.nextBytes(randomToken);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomToken);
        byte[] tokenHash = sha256(token);

        ResetDelivery delivery = transaction.execute(status -> {
            OptionalLong userId = resetDao.findUserIdByEmailForUpdate(email);
            if (userId.isEmpty()) return null;
            long id = userId.getAsLong();
            if (resetDao.hasRecentResetRequest(id, accountCooldownSeconds)) return null;
            resetDao.consumeOutstandingTokens(id);
            resetDao.insertToken(id, tokenHash);
            return new ResetDelivery(id, tokenHash, token);
        });
        if (delivery != null) {
            userDao.findById(delivery.userId()).ifPresent(user ->
                    mailDelivery.deliver(user.email(), delivery.tokenHash(), delivery.token(), requestId));
        }
    }

    public void completeReset(String token, String password, String confirmation) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new InvalidUserInputException("Password must be between 8 and 128 characters.");
        }
        if (!password.equals(confirmation)) {
            throw new InvalidUserInputException("The passwords do not match.");
        }
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new InvalidUserInputException(INVALID_TOKEN_MESSAGE);
        }
        byte[] hash = sha256(token);
        String passwordHash = passwordHasher.hash(password);
        Boolean consumed = transaction.execute(status -> resetDao.consumeTokenAndUpdatePassword(hash, passwordHash));
        if (!Boolean.TRUE.equals(consumed)) {
            throw new InvalidUserInputException(INVALID_TOKEN_MESSAGE);
        }
    }

    private byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Secure token hashing is unavailable.", exception);
        }
    }

    private record ResetDelivery(long userId, byte[] tokenHash, String token) { }
}
