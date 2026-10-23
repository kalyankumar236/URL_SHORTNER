package com.agentic.platform.urlshortener.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates unique, unpredictable short codes using a cryptographically strong RNG
 * over a base62 alphabet. Uniqueness under concurrency is guaranteed by the DB unique
 * constraint; this generator only proposes candidates.
 */
@Component
public class ShortCodeGenerator {

    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private final SecureRandom random = new SecureRandom();

    public String generate(int length) {
        if (length < 4) {
            throw new IllegalArgumentException("code length must be >= 4");
        }
        char[] buf = new char[length];
        for (int i = 0; i < length; i++) {
            buf[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(buf);
    }
}
