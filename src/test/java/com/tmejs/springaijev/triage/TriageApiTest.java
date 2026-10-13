package com.tmejs.springaijev.triage;

import com.tmejs.springaijev.auth.AppUser;
import com.tmejs.springaijev.auth.AppUserRepository;
import com.tmejs.springaijev.auth.AppUserRole;
import com.tmejs.springaijev.auth.JwtService;
import com.tmejs.springaijev.issue.Issue;
import com.tmejs.springaijev.issue.IssueRepository;
import com.tmejs.springaijev.issue.IssueStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class TriageApiTest {
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
    @Autowired TriageRepository triages;
    @Autowired JwtService tokens;
    @MockitoBean JevClient jev;

    AppUser owner;
    AppUser stranger;
    AppUser maintainer;
    Issue issue;

    @BeforeEach
    void data() {
        triages.deleteAll();
        issues.deleteAll();
        users.deleteAll();
        owner = users.save(new AppUser("triage-owner", "hash", AppUserRole.USER));
        stranger = users.save(new AppUser("triage-stranger", "hash", AppUserRole.USER));
        maintainer = users.save(new AppUser("triage-maintainer", "hash", AppUserRole.MAINTAINER));
        issue = issues.save(new Issue(owner, "Login fails", "The login button returns 500"));
    }

    @Test
    void maintainerTriageStoresTypedAnswersAndOwnerCanReadHistory() throws Exception {
        when(jev.assess("Login fails", "The login button returns 500")).thenReturn(assessment());
        mvc.perform(post("/api/v1/issues/{id}/triage", issue.getId())
                .header("Authorization", bearer(maintainer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.category").value("BUG"))
            .andExpect(jsonPath("$.status").value("TRIAGED"))
            .andExpect(jsonPath("$.categoryConfidence").value(0.83));
        assertEquals(IssueStatus.TRIAGED, issues.findById(issue.getId()).orElseThrow().getStatus());
        assertEquals(1, triages.count());
        mvc.perform(get("/api/v1/issues/{id}/triage", issue.getId())
                .header("Authorization", bearer(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].model").value("jev-1.13.0"));
    }

    @Test
    void userCannotRunTriageAndForeignUserCannotReadHistory() throws Exception {
        mvc.perform(post("/api/v1/issues/{id}/triage", issue.getId())
                .header("Authorization", bearer(owner)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/issues/{id}/triage", issue.getId())
                .header("Authorization", bearer(stranger)))
            .andExpect(status().isNotFound());
    }

    @Test
    void malformedJevResponseDoesNotChangeIssue() throws Exception {
        when(jev.assess("Login fails", "The login button returns 500"))
            .thenThrow(new JevProtocolException("bad answer"));
        mvc.perform(post("/api/v1/issues/{id}/triage", issue.getId())
                .header("Authorization", bearer(maintainer)))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.retryable").value(false));
        assertEquals(IssueStatus.UNTRIAGED, issues.findById(issue.getId()).orElseThrow().getStatus());
        assertEquals(0, triages.count());
    }

    @Test
    void providerOutagePreservesPreviousTriageForRetry() throws Exception {
        when(jev.assess("Login fails", "The login button returns 500"))
            .thenReturn(assessment())
            .thenThrow(new JevUnavailableException("offline"));
        mvc.perform(post("/api/v1/issues/{id}/triage", issue.getId())
                .header("Authorization", bearer(maintainer)))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/issues/{id}/triage", issue.getId())
                .header("Authorization", bearer(maintainer)))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.retryable").value(true));
        assertEquals(IssueStatus.TRIAGED, issues.findById(issue.getId()).orElseThrow().getStatus());
        assertEquals(1, triages.count());
    }

    private JevAssessment assessment() {
        return new JevAssessment("jev-1.13.0", JevAssessment.Category.BUG, 0.83,
            Map.of("bug", 0.9, "feature", 0.05, "question", 0.05), 1.4, 0.7,
            Map.of("0", 0.1, "1", 0.4, "2", 0.5), 0.91);
    }

    private String bearer(AppUser user) {
        return "Bearer " + tokens.issue(user).accessToken();
    }
}
