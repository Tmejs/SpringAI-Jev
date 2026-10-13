package com.tmejs.springaijev.triage;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class TypeSafeJevClientTest {
    private MockRestServiceServer server;
    private TypeSafeJevClient client;

    @BeforeEach
    void clientWithStubServer() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TypeSafeJevClient(builder, "https://api.typesafe.ai", "test-api-key");
    }

    @Test
    void sendsThreeTypedQuestionsAndMapsValidAnswers() {
        server.expect(once(), requestTo("https://api.typesafe.ai/v1/systemone"))
            .andExpect(method(POST))
            .andExpect(header("Authorization", "Bearer test-api-key"))
            .andExpect(jsonPath("$.model").value("jev-latest"))
            .andExpect(jsonPath("$.state.title").value("Login fails"))
            .andExpect(jsonPath("$.questions.category.type").value("choice"))
            .andExpect(jsonPath("$.questions.urgency.type").value("score"))
            .andExpect(jsonPath("$.questions.sufficient_information.type").value("noul"))
            .andRespond(withSuccess(validResponse(), MediaType.APPLICATION_JSON));

        JevAssessment answer = client.assess("Login fails", "The login button returns 500");
        assertEquals("jev-1.13.0", answer.model());
        assertEquals(JevAssessment.Category.BUG, answer.category());
        assertEquals(0.83, answer.categoryConfidence());
        assertEquals(1.4, answer.urgencyScore());
        assertEquals(0.91, answer.sufficientInformationProbability());
        server.verify();
    }

    @Test
    void missingAnswerIsProtocolError() {
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
            .andRespond(withSuccess("{\"model\":\"jev-1.13.0\",\"answers\":{}}", MediaType.APPLICATION_JSON));
        assertThrows(JevProtocolException.class, () -> client.assess("title", "description"));
    }

    @Test
    void unknownCategoryIsProtocolError() {
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
            .andRespond(withSuccess(validResponse().replace("\"bug\"", "\"support\""), MediaType.APPLICATION_JSON));
        assertThrows(JevProtocolException.class, () -> client.assess("title", "description"));
    }

    @Test
    void outOfRangeProbabilityIsProtocolError() {
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
            .andRespond(withSuccess(validResponse().replace("\"noul\":0.91", "\"noul\":1.4"),
                MediaType.APPLICATION_JSON));
        assertThrows(JevProtocolException.class, () -> client.assess("title", "description"));
    }

    @Test
    void invalidJsonIsProtocolError() {
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
            .andRespond(withSuccess("{invalid", MediaType.APPLICATION_JSON));
        assertThrows(JevProtocolException.class, () -> client.assess("title", "description"));
    }

    @Test
    void serverErrorIsUnavailable() {
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone")).andRespond(withServerError());
        assertThrows(JevUnavailableException.class, () -> client.assess("title", "description"));
    }

    @Test
    void timeoutIsUnavailable() {
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
            .andRespond(withException(new IOException("timeout")));
        assertThrows(JevUnavailableException.class, () -> client.assess("title", "description"));
    }

    private String validResponse() {
        return """
            {"model":"jev-1.13.0","answers":{
              "category":{"type":"choice","choice":"bug","confidence":0.83,
                "probabilities":{"bug":0.9,"feature":0.05,"question":0.05}},
              "urgency":{"type":"score","score":1.4,"confidence":0.7,
                "probabilities":{"0":0.1,"1":0.4,"2":0.5}},
              "sufficient_information":{"type":"noul","noul":0.91}
            },"usage":{"input_tokens":100,"output_tokens":20}}
            """;
    }
}
