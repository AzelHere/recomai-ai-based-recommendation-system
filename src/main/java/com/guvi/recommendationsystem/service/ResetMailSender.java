package com.guvi.recommendationsystem.service;

public interface ResetMailSender {
    void sendResetLink(String recipient, String resetUrl);
}
