package com.tmejs.springaijev.triage;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TypeSafeJevClient implements JevClient {
    private final RestClient client;
    private final String apiKey;

    @Autowired
    public TypeSafeJevClient(@Value("${app.jev.base-url}") String baseUrl,
            @Value("${app.jev.api-key}") String apiKey) {
        this(configuredBuilder(), baseUrl, apiKey);
    }

    public TypeSafeJevClient(RestClient.Builder builder, String baseUrl, String apiKey) {
        this.client = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    private static RestClient.Builder configuredBuilder() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory);
    }

    @Override
    public JevAssessment assess(String title, String description) {
        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put("category", Map.of(
            "type", "choice",
            "instructions", "Which kind of software issue is this?",
            "criteria", Map.of(
                "bug", "An existing behavior fails or produces an error",
                "feature", "A request for new or changed behavior",
                "question", "A request for help or clarification")));
        questions.put("urgency", Map.of(
            "type", "score",
            "instructions", "How urgent is this issue for a maintainer?",
            "criteria", List.of(
                "Routine: no immediate impact is described",
                "Time-sensitive: a user is blocked or work is delayed",
                "Critical: broad outage, data loss, or serious security impact is described")));
        questions.put("sufficient_information", Map.of(
            "type", "noul",
            "instructions", "Does the issue contain enough information for a maintainer to act?"));

        Map<String, Object> request = Map.of(
            "model", "jev-latest",
            "state", Map.of("title", title, "description", description),
            "questions", questions);
        try {
            Map<?, ?> raw = client.post().uri("/v1/systemone")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(request).retrieve().body(Map.class);
            return parse(raw);
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 429 || error.getStatusCode().is5xxServerError()) {
                throw new JevUnavailableException("Jev service is unavailable", error);
            }
            throw new JevProtocolException("Jev rejected the request", error);
        } catch (ResourceAccessException error) {
            throw new JevUnavailableException("Jev service is unavailable", error);
        } catch (RestClientException error) {
            throw new JevProtocolException("Jev returned an invalid response", error);
        }
    }

    private JevAssessment parse(Object raw) {
        Map<String, Object> response = object(raw);
        String model = string(response.get("model"));
        Map<String, Object> answers = object(response.get("answers"));
        Map<String, Object> category = object(answers.get("category"));
        requireType(category, "choice");
        String chosen = string(category.get("choice"));
        JevAssessment.Category categoryValue = switch (chosen) {
            case "bug" -> JevAssessment.Category.BUG;
            case "feature" -> JevAssessment.Category.FEATURE;
            case "question" -> JevAssessment.Category.QUESTION;
            default -> throw new JevProtocolException("Jev returned an unknown category");
        };
        double categoryConfidence = number(category.get("confidence"), 1);
        Map<String, Double> categoryProbabilities = probabilities(category.get("probabilities"),
            List.of("bug", "feature", "question"));

        Map<String, Object> urgency = object(answers.get("urgency"));
        requireType(urgency, "score");
        double urgencyScore = number(urgency.get("score"), 2);
        double urgencyConfidence = number(urgency.get("confidence"), 1);
        Map<String, Double> urgencyProbabilities = probabilities(urgency.get("probabilities"),
            List.of("0", "1", "2"));

        Map<String, Object> sufficient = object(answers.get("sufficient_information"));
        requireType(sufficient, "noul");
        double sufficientProbability = number(sufficient.get("noul"), 1);
        return new JevAssessment(model, categoryValue, categoryConfidence,
            categoryProbabilities, urgencyScore, urgencyConfidence,
            urgencyProbabilities, sufficientProbability);
    }

    private Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new JevProtocolException("Jev returned a missing or invalid object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new JevProtocolException("Jev returned an invalid key");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private String string(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new JevProtocolException("Jev returned a missing or invalid string");
        }
        return text;
    }

    private void requireType(Map<String, Object> answer, String expected) {
        if (!expected.equals(answer.get("type"))) {
            throw new JevProtocolException("Jev returned an unexpected answer type");
        }
    }

    private double number(Object value, double maximum) {
        if (!(value instanceof Number numeric)) {
            throw new JevProtocolException("Jev returned a missing numeric value");
        }
        double result = numeric.doubleValue();
        if (!Double.isFinite(result) || result < 0 || result > maximum) {
            throw new JevProtocolException("Jev returned an out-of-range value");
        }
        return result;
    }

    private Map<String, Double> probabilities(Object raw, List<String> keys) {
        Map<String, Object> values = object(raw);
        Map<String, Double> result = new LinkedHashMap<>();
        for (String key : keys) {
            result.put(key, number(values.get(key), 1));
        }
        if (values.size() != keys.size()) {
            throw new JevProtocolException("Jev returned unexpected probability keys");
        }
        return Map.copyOf(result);
    }
}
