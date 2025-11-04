package com.pukazhya.oibsip.task3;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for secure password hashing and verification using SHA-256 and random salt.
 * This class ensures passwords are never stored or compared in plain text.
 *
 * @author PUKAZHYA
 * @version 2.0
 */
public final class HashUtil {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 16;

    // Private constructor to prevent instantiation
    private HashUtil() {}

    /**
     * Generates a random salt and hashes the input password securely.
     *
     * @param password The plain text password to hash.
     * @return A Base64-encoded string containing both the salt and the hash.
     */
    public static String hashPassword(String password) {
        try {
            byte[] salt = generateSalt();
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            digest.update(salt);
            byte[] hashedPassword = digest.digest(password.getBytes());

            // Combine salt + hash into one byte array
            byte[] combined = new byte[salt.length + hashedPassword.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hashedPassword, 0, combined, salt.length, hashedPassword.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error initializing hash algorithm: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies if a plain text password matches the stored hash.
     *
     * @param password   The plain text password to verify.
     * @param storedHash The Base64-encoded stored hash containing salt + hash.
     * @return true if passwords match, false otherwise.
     */
    public static boolean verifyPassword(String password, String storedHash) {
        try {
            byte[] combined = Base64.getDecoder().decode(storedHash);

            // Extract salt and stored hash from combined bytes
            byte[] salt = new byte[SALT_LENGTH];
            byte[] originalHash = new byte[combined.length - SALT_LENGTH];
            System.arraycopy(combined, 0, salt, 0, SALT_LENGTH);
            System.arraycopy(combined, SALT_LENGTH, originalHash, 0, originalHash.length);

            // Hash the entered password with the same salt
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            digest.update(salt);
            byte[] newHash = digest.digest(password.getBytes());

            // Constant-time comparison (avoids timing attacks)
            return slowEquals(originalHash, newHash);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Securely generates a random salt.
     *
     * @return A random salt of SALT_LENGTH bytes.
     */
    private static byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return salt;
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    private static boolean slowEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
