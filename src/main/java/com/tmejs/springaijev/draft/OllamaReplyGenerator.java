package com.tmejs.springaijev.draft;

import com.tmejs.springaijev.issue.Issue;
import com.tmejs.springaijev.triage.TriageResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class OllamaReplyGenerator implements ReplyGenerator {
    private final ChatClient chatClient;

    public OllamaReplyGenerator(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String generate(Issue issue, TriageResult latest) {
        try {
            return chatClient.prompt()
                .system("Draft a concise, editable maintainer reply to a software issue. "
                    + "Do not claim a fix or promise a feature or delivery date. "
                    + "Acknowledge the report and ask for missing details where useful.")
                .user("Issue title: " + issue.getTitle() + "\nIssue description: "
                    + issue.getDescription() + "\nTriage: " + latest.summary())
                .call().content();
        } catch (RuntimeException error) {
            throw new ReplyUnavailableException("Ollama request failed", error);
        }
    }
}
