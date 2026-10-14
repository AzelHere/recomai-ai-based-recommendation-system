package com.guvi.recommendationsystem.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Pbkdf2PasswordHasherTest {
    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();

    @Test
    void storesDifferentSaltedHashesForTheSamePassword() {
        String first = hasher.hash("CorrectHorse9!");
        String second = hasher.hash("CorrectHorse9!");

        assertThat(first).isNotEqualTo(second);
        assertThat(first).startsWith("pbkdf2-sha256$600000$");
        assertThat(hasher.matches("CorrectHorse9!", first)).isTrue();
        assertThat(hasher.matches("wrong-password", first)).isFalse();
    }

    @Test
    void rejectsMalformedEncodedHashes() {
        assertThat(hasher.matches("password", "invalid-format")).isFalse();
        assertThat(hasher.matches("password", null)).isFalse();
    }
}
