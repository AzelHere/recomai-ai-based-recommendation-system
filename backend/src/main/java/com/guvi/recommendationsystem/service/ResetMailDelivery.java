package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.PasswordResetDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ResetMailDelivery {
    private static final Logger logger = LoggerFactory.getLogger(ResetMailDelivery.class);
    private final ResetMailSender mailSender;
    private final PasswordResetDao resetDao;
    private final String publicUrl;

    public ResetMailDelivery(ResetMailSender mailSender, PasswordResetDao resetDao,
                             @org.springframework.beans.factory.annotation.Value("${app.public-url:http://localhost:8080}") String publicUrl) {
        this.mailSender = mailSender;
        this.resetDao = resetDao;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Async("applicationTaskExecutor")
    public void deliver(String recipient, byte[] tokenHash, String token, String requestId) {
        String link = publicUrl + "/#reset-password?token="
                + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
        try {
            mailSender.sendResetLink(recipient, link);
        } catch (RuntimeException failure) {
            resetDao.invalidateByHash(tokenHash);
            logger.warn("Password reset email delivery failed requestId={} reason={}",
                    requestId, failure.getClass().getSimpleName());
        }
    }
}
