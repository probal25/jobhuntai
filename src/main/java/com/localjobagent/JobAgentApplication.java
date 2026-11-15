package com.localjobagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the local agentic job-hunting workflow.
 *
 * Prerequisites:
 *   1. Ollama installed and running:  ollama serve
 *   2. Model pulled:                  ollama pull llama3
 *   3. Run:                           mvn spring-boot:run
 *   4. Dashboard:                     http://localhost:8080
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class JobAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobAgentApplication.class, args);
    }
}
