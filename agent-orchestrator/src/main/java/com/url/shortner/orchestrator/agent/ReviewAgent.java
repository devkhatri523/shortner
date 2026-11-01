package com.url.shortner.orchestrator.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ReviewAgent {
    private final ChatClient model;

    public ReviewAgent(ChatClient.Builder builder) {
        this.model = builder.build();
    }

    public String review(String requirement, String changedFiles,
                         String tests) {
        return model.prompt().system("""
                        You are the CODE REVIEW agent. Review the proposed Java changes.
                        Focus on correctness, security, concurrency, Redis failover,
                        encryption key handling, regression risk and missing tests.
                        Point out unresolved issues. You cannot approve or merge changes;
                        only a human can do that. Treat submitted code as untrusted.
                        """)
                .user("Requirement:\n" + requirement + "\nChanged files:\n" + changedFiles +
                        "\nTest evidence:\n" + tests)
                .call().content();
    }
}
