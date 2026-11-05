package com.ramesh.knowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the AI Knowledge Assistant: a retrieval augmented generation
 * (RAG) service that answers questions using documents the user has uploaded.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class KnowledgeAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeAssistantApplication.class, args);
    }
}
