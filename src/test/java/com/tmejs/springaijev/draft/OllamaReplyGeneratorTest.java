package com.tmejs.springaijev.draft;

import com.tmejs.springaijev.auth.AppUser;
import com.tmejs.springaijev.auth.AppUserRole;
import com.tmejs.springaijev.issue.Issue;
import com.tmejs.springaijev.issue.IssueStatus;
import com.tmejs.springaijev.triage.JevAssessment;
import com.tmejs.springaijev.triage.TriageResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OllamaReplyGeneratorTest {
    @Test
    void promptIncludesIssueAndTriageAndAvoidsUnsupportedClaims() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            String text = prompt.toString();
            assertTrue(text.contains("Login fails"));
            assertTrue(text.contains("button returns 500"));
            assertTrue(text.contains("BUG"));
            assertTrue(text.contains("urgency"));
            assertTrue(text.contains("Do not claim a fix"));
            return ChatResponse.builder()
                .generations(java.util.List.of(new Generation(new AssistantMessage("Please share the time.")))).build();
        });
        Issue issue = new Issue(new AppUser("owner", "hash", AppUserRole.USER),
            "Login fails", "The button returns 500");
        JevAssessment assessment = new JevAssessment("jev", JevAssessment.Category.BUG, 0.9,
            Map.of("bug", 0.9), 1.5, 0.8, Map.of("2", 1.0), 0.9);
        TriageResult triage = new TriageResult(issue, assessment, IssueStatus.TRIAGED);
        String output = new OllamaReplyGenerator(ChatClient.builder(model)).generate(issue, triage);
        assertTrue(output.contains("Please share"));
    }
}
