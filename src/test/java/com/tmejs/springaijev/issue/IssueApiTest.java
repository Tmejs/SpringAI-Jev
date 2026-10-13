package com.tmejs.springaijev.issue;

import com.jayway.jsonpath.JsonPath;
import com.tmejs.springaijev.auth.AppUser;
import com.tmejs.springaijev.auth.AppUserRepository;
import com.tmejs.springaijev.auth.AppUserRole;
import com.tmejs.springaijev.auth.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class IssueApiTest {
    @Container
    static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", database::getJdbcUrl);
        properties.add("spring.datasource.username", database::getUsername);
        properties.add("spring.datasource.password", database::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired IssueRepository issues;
    @Autowired JwtService tokens;

    AppUser owner;
    AppUser stranger;
    AppUser maintainer;

    @BeforeEach
    void accounts() {
        issues.deleteAll();
        users.deleteAll();
        owner = users.save(new AppUser("issue-owner", "hash", AppUserRole.USER));
        stranger = users.save(new AppUser("issue-stranger", "hash", AppUserRole.USER));
        maintainer = users.save(new AppUser("issue-maintainer", "hash", AppUserRole.MAINTAINER));
    }

    @Test
    void ownerCanSubmitAndReadIssue() throws Exception {
        UUID id = submit(owner);
        mvc.perform(get("/api/v1/issues/{id}", id).header("Authorization", bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UNTRIAGED"))
            .andExpect(jsonPath("$.title").value("Login fails"));
    }

    @Test
    void foreignUserGetsNotFoundButMaintainerCanRead() throws Exception {
        UUID id = submit(owner);
        mvc.perform(get("/api/v1/issues/{id}", id).header("Authorization", bearer(stranger)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/issues/{id}", id).header("Authorization", bearer(maintainer)))
            .andExpect(status().isOk());
    }

    @Test
    void userListOnlyContainsOwnedIssues() throws Exception {
        submit(owner);
        submit(stranger);
        mvc.perform(get("/api/v1/issues").header("Authorization", bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].ownerId").value(owner.getId().toString()));
        mvc.perform(get("/api/v1/issues?status=UNTRIAGED")
                .header("Authorization", bearer(maintainer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void invalidTitleAndOversizedDescriptionAreBadRequests() throws Exception {
        mvc.perform(post("/api/v1/issues").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \",\"description\":\"repro\"}"))
            .andExpect(status().isBadRequest());
        String oversized = "x".repeat(10_001);
        mvc.perform(post("/api/v1/issues").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Login fails\",\"description\":\"" + oversized + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void missingTokenUsesSafeErrorShape() throws Exception {
        mvc.perform(get("/api/v1/issues"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.retryable").value(false));
    }

    private UUID submit(AppUser user) throws Exception {
        String response = mvc.perform(post("/api/v1/issues")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Login fails\",\"description\":\"The login button returns 500\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("UNTRIAGED"))
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private String bearer(AppUser user) {
        return "Bearer " + tokens.issue(user).accessToken();
    }
}
