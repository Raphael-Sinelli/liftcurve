package com.rsinelli.gymtracker.unit;

import com.rsinelli.gymtracker.security.PasswordHasher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void hashesPasswordAndVerifiesMatch() {
        String hash = passwordHasher.hash("correct-horse-battery-staple");

        assertTrue(passwordHasher.matches("correct-horse-battery-staple", hash));
    }

    @Test
    void rejectsWrongPassword() {
        String hash = passwordHasher.hash("correct-horse-battery-staple");

        assertFalse(passwordHasher.matches("wrong-password", hash));
    }

    @Test
    void producesDifferentHashesForSamePasswordDueToSalt() {
        String hash1 = passwordHasher.hash("same-password");
        String hash2 = passwordHasher.hash("same-password");

        assertNotEquals(hash1, hash2);
    }
}
