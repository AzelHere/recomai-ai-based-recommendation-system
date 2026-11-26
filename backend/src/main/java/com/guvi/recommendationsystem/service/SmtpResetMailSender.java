package com.guvi.recommendationsystem.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpResetMailSender implements ResetMailSender {
    private final JavaMailSender mailSender;
    private final String from;

    public SmtpResetMailSender(JavaMailSender mailSender,
                               @Value("${app.mail.from:no-reply@localhost}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendResetLink(String recipient, String resetUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Reset your Recom AI password");
        message.setText("Use this link within 15 minutes to reset your password:\n\n" + resetUrl
                + "\n\nIf you did not request this, you can ignore this message.");
        mailSender.send(message);
    }
}
