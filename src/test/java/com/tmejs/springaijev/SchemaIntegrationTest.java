package com.tmejs.springaijev;

import com.tmejs.springaijev.auth.AppUser;
import com.tmejs.springaijev.auth.AppUserRepository;
import com.tmejs.springaijev.auth.AppUserRole;
import com.tmejs.springaijev.issue.Issue;
import com.tmejs.springaijev.issue.IssueRepository;
import com.tmejs.springaijev.issue.IssueStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SchemaIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", database::getJdbcUrl);
        properties.add("spring.datasource.username", database::getUsername);
        properties.add("spring.datasource.password", database::getPassword);
    }

    @Autowired AppUserRepository users;
    @Autowired IssueRepository issues;

    @Test
    void issueStartsUntriagedAndRetainsOwner() {
        var user = users.save(new AppUser("schema-user", "hashed-password", AppUserRole.USER));
        var issue = issues.save(new Issue(user, "Login fails", "The login button returns 500"));

        var reloaded = issues.findById(issue.getId()).orElseThrow();
        assertEquals(IssueStatus.UNTRIAGED, reloaded.getStatus());
        assertEquals(user.getId(), reloaded.getOwner().getId());
    }
}
