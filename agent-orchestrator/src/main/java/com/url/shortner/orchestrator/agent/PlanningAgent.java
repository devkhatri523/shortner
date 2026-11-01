package com.url.shortner.orchestrator.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class PlanningAgent {
    private final ChatClient model;

    public PlanningAgent(ChatClient.Builder builder) {
        this.model = builder.build();
    }

    public String plan(String requirement, String analysis) {
        return model.prompt()
                .system("""
                        You are the PLANNING agent. Explain the changes needed in a Spring Boot
                        URL shortener, the impacted Java files, tests and technical risks.
                        Propose a short implementation plan; the application, not you,
                        owns the mandatory task graph and the approval gates.
                        Never propose skipping tests or disabling checks.
                        """)
                .user("Requirement:\n" + requirement + "\nAnalysis:\n" + analysis)
                .call().content();
    }
}
