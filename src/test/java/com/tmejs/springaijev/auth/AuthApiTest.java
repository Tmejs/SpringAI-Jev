package com.tmejs.springaijev.auth;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(AuthApiTest.TestController.class)
class AuthApiTest {
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
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;

    @BeforeEach
    void accounts() {
        users.deleteAll();
        var bcrypt = new BCryptPasswordEncoder();
        users.save(new AppUser("demo-user", bcrypt.encode("correct-password"), AppUserRole.USER));
        users.save(new AppUser("demo-maintainer", bcrypt.encode("maintainer-password"), AppUserRole.MAINTAINER));
    }

    @Test
    void loginIssuesSignedTokenWithRoleAndSubject() throws Exception {
        String token = login("demo-user", "correct-password");
        var decoded = decoder.decode(token);
        assertEquals("USER", decoded.getClaimAsString("role"));
        assertEquals(users.findByUsername("demo-user").orElseThrow().getId().toString(), decoded.getSubject());
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"demo-user\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/maintainer/ping"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        var claims = JwtClaimsSet.builder().issuer("springai-jev")
            .subject(users.findByUsername("demo-user").orElseThrow().getId().toString())
            .issuedAt(past).expiresAt(past.plusSeconds(30)).claim("role", "USER").build();
        String token = encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/v1/maintainer/ping").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotReachMaintainerRoute() throws Exception {
        mvc.perform(get("/api/v1/maintainer/ping")
                .header("Authorization", "Bearer " + login("demo-user", "correct-password")))
            .andExpect(status().isForbidden());
    }

    private String login(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        var response = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isString())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    @RestController
    static class TestController {
        @GetMapping("/api/v1/maintainer/ping")
        String ping() { return "pong"; }
    }
}
