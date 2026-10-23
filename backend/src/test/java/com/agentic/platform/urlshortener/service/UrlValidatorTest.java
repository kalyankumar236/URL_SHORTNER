package com.agentic.platform.urlshortener.service;

import com.agentic.platform.common.ApiException;
import com.agentic.platform.common.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    private UrlValidator validator;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        validator = new UrlValidator(props);
    }

    @Test
    void acceptsHttpAndHttps() {
        assertThat(validator.validateAndNormalize("http://example.com/path")).isEqualTo("http://example.com/path");
        assertThat(validator.validateAndNormalize("https://Example.com/Path?q=1#frag"))
                .isEqualTo("https://example.com/Path?q=1#frag");
    }

    @Test
    void lowercasesSchemeAndHostOnly() {
        // Path case is preserved so destination meaning is not silently changed.
        assertThat(validator.validateAndNormalize("HTTPS://API.Example.COM/CaseSensitivePath"))
                .isEqualTo("https://api.example.com/CaseSensitivePath");
    }

    @Test
    void rejectsJavascriptScheme() {
        assertThatThrownBy(() -> validator.validateAndNormalize("javascript:alert(1)"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("scheme");
    }

    @Test
    void rejectsDataAndFileSchemes() {
        assertThatThrownBy(() -> validator.validateAndNormalize("data:text/html,<script>"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validateAndNormalize("file:///etc/passwd"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsMissingHost() {
        assertThatThrownBy(() -> validator.validateAndNormalize("http://"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> validator.validateAndNormalize("  "))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsOverlongUrl() {
        String longUrl = "http://example.com/" + "a".repeat(3000);
        assertThatThrownBy(() -> validator.validateAndNormalize(longUrl))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("maximum length");
    }
}
