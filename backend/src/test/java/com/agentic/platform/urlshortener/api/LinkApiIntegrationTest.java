package com.agentic.platform.urlshortener.api;

import com.agentic.platform.urlshortener.domain.Link;
import com.agentic.platform.urlshortener.domain.LinkStatus;
import com.agentic.platform.urlshortener.repo.LinkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LinkApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired LinkRepository linkRepo;

    private String json(Object o) throws Exception {
        return mapper.writeValueAsString(o);
    }

    @Test
    void createAndRedirect() throws Exception {
        String body = mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("destinationUrl", "https://example.com/welcome"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode", not(emptyString())))
                .andExpect(jsonPath("$.destinationUrl", is("https://example.com/welcome")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andReturn().getResponse().getContentAsString();

        String code = mapper.readTree(body).get("shortCode").asText();

        mvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/welcome"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        mvc.perform(get("/does-not-exist-code"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("NOT_FOUND")));
    }

    @Test
    void rejectsDangerousScheme() throws Exception {
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("destinationUrl", "javascript:alert(1)"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));
    }

    @Test
    void customAliasAndDuplicateConflict() throws Exception {
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("destinationUrl", "https://example.com/a", "alias", "my-alias"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode", is("my-alias")))
                .andExpect(jsonPath("$.customAlias", is(true)));

        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("destinationUrl", "https://example.com/b", "alias", "my-alias"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("CONFLICT")));
    }

    @Test
    void reservedAliasRejected() throws Exception {
        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("destinationUrl", "https://example.com/x", "alias", "admin"))))
                .andExpect(status().isConflict());
    }

    @Test
    void expiredLinkReturnsGone() throws Exception {
        // Persist an already-expired link directly, then confirm redirect yields 410.
        Link link = new Link("expired1", "https://example.com/old", false,
                Instant.now().minus(1, ChronoUnit.HOURS), "test");
        // expiresAt set via constructor; but constructor validation is only in service, so direct save is fine.
        linkRepo.save(forceExpired(link));
        mvc.perform(get("/expired1"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error", is("GONE")));
    }

    @Test
    void listSupportsPagination() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("destinationUrl", "https://example.com/p" + i))));
        }
        mvc.perform(get("/api/v1/links?page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(2)))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void deleteRequiresAdmin() throws Exception {
        String body = mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("destinationUrl", "https://example.com/del"))))
                .andReturn().getResponse().getContentAsString();
        String code = mapper.readTree(body).get("shortCode").asText();

        // Unauthenticated delete is rejected.
        mvc.perform(delete("/api/v1/links/" + code))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanDisableLink() throws Exception {
        String body = mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("destinationUrl", "https://example.com/del2"))))
                .andReturn().getResponse().getContentAsString();
        String code = mapper.readTree(body).get("shortCode").asText();

        mvc.perform(delete("/api/v1/links/" + code)).andExpect(status().isNoContent());
        mvc.perform(get("/" + code)).andExpect(status().isGone());
    }

    /** The persisted link already has a past expiry; status stays ACTIVE and expiry drives GONE. */
    private Link forceExpired(Link link) {
        try {
            Field status = Link.class.getDeclaredField("status");
            status.setAccessible(true);
            status.set(link, LinkStatus.ACTIVE);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return link;
    }
}
