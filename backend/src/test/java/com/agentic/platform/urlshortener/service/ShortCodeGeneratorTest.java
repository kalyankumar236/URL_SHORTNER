package com.agentic.platform.urlshortener.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    void generatesCodeOfRequestedLength() {
        assertThat(generator.generate(7)).hasSize(7);
    }

    @Test
    void generatesBase62Only() {
        assertThat(generator.generate(20)).matches("[A-Za-z0-9]+");
    }

    @Test
    void generatesHighlyUniqueCodes() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            seen.add(generator.generate(7));
        }
        // Collisions in 5000 draws over 62^7 space should be effectively zero.
        assertThat(seen).hasSizeGreaterThan(4990);
    }

    @Test
    void rejectsTooShortLength() {
        assertThatThrownBy(() -> generator.generate(3))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
