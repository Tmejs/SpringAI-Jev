package com.tmejs.springaijev.draft;

import com.tmejs.springaijev.auth.AppUser;
import com.tmejs.springaijev.auth.AppUserRepository;
import com.tmejs.springaijev.auth.AppUserRole;
import com.tmejs.springaijev.auth.JwtService;
import com.tmejs.springaijev.issue.Issue;
import com.tmejs.springaijev.issue.IssueRepository;
import com.tmejs.springaijev.issue.IssueStatus;
import com.tmejs.springaijev.triage.JevAssessment;
import com.tmejs.springaijev.triage.TriageRepository;
import com.tmejs.springaijev.triage.TriageResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class DraftApiTest {
    @Container static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17");

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
    @Autowired ReplyDraftRepository drafts;
    @Autowired JwtService tokens;
    @MockitoBean ReplyGenerator generator;

    AppUser owner;
    AppUser maintainer;
    Issue issue;

    @BeforeEach
    void data() {
        drafts.deleteAll();
        triages.deleteAll();
        issues.deleteAll();
        users.deleteAll();
        owner = users.save(new AppUser("draft-owner", "hash", AppUserRole.USER));
        maintainer = users.save(new AppUser("draft-maintainer", "hash", AppUserRole.MAINTAINER));
        issue = issues.save(new Issue(owner, "Login fails", "The login button returns 500"));
        triages.save(new TriageResult(issue, assessment(), IssueStatus.TRIAGED));
    }

    @Test
    void generatesListsAndEditsStoredDraft() throws Exception {
        when(generator.generate(any(), any())).thenReturn("We are investigating.");
        String body = mvc.perform(post("/api/v1/issues/{id}/drafts", issue.getId())
                .header("Authorization", bearer(maintainer)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.content").value("We are investigating."))
            .andReturn().getResponse().getContentAsString();
        UUID draftId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id"));
        mvc.perform(get("/api/v1/issues/{id}/drafts", issue.getId())
                .header("Authorization", bearer(maintainer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(patch("/api/v1/issues/{id}/drafts/{draftId}", issue.getId(), draftId)
                .header("Authorization", bearer(maintainer))
                .contentType("application/json")
                .content("{\"content\":\"Please share the error time.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("Please share the error time."));
        assertEquals("Please share the error time.", drafts.findById(draftId).orElseThrow().getContent());
    }

    @Test
    void rejectsBlankOutputAndUnavailableModelWithoutSaving() throws Exception {
        when(generator.generate(any(), any())).thenReturn("  ");
        mvc.perform(post("/api/v1/issues/{id}/drafts", issue.getId())
                .header("Authorization", bearer(maintainer)))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.retryable").value(false));
        when(generator.generate(any(), any())).thenThrow(new ReplyUnavailableException("offline"));
        mvc.perform(post("/api/v1/issues/{id}/drafts", issue.getId())
                .header("Authorization", bearer(maintainer)))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.retryable").value(true));
        assertEquals(0, drafts.count());
    }

    @Test
    void requiresTriageAndMaintainerRole() throws Exception {
        mvc.perform(post("/api/v1/issues/{id}/drafts", issue.getId())
                .header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        Issue untriaged = issues.save(new Issue(owner, "New issue", "No triage yet"));
        mvc.perform(post("/api/v1/issues/{id}/drafts", untriaged.getId())
                .header("Authorization", bearer(maintainer))).andExpect(status().isConflict());
    }

    @Test
    void cannotEditDraftThroughAnotherIssue() throws Exception {
        ReplyDraft saved = drafts.save(new ReplyDraft(issue, maintainer, "Draft"));
        Issue other = issues.save(new Issue(owner, "Other", "Other issue"));
        mvc.perform(patch("/api/v1/issues/{id}/drafts/{draftId}", other.getId(), saved.getId())
                .header("Authorization", bearer(maintainer))
                .contentType("application/json")
                .content("{\"content\":\"Changed\"}"))
            .andExpect(status().isNotFound());
        assertEquals("Draft", drafts.findById(saved.getId()).orElseThrow().getContent());
    }

    private JevAssessment assessment() {
        return new JevAssessment("jev-1.13.0", JevAssessment.Category.BUG, 0.83,
            Map.of("bug", 0.9), 1.4, 0.7, Map.of("1", 1.0), 0.91);
    }

    private String bearer(AppUser user) {
        return "Bearer " + tokens.issue(user).accessToken();
    }
}
