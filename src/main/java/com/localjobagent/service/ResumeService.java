package com.localjobagent.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Shared service to manage the Master Resume.
 * Ensures that TriageAgent and ResumeAdapterAgent use the same source of truth.
 */
@Service
public class ResumeService {
    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private final ResourceLoader resourceLoader;

    @Value("${app.resume.path:master-resume.tex}")
    private String resumePath;

    private String cachedResume;

    public ResumeService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        loadResume();
    }

    private void loadResume() {
        log.info("Loading master resume from: {}", resumePath);
        try {
            Resource resource = resourceLoader.getResource("classpath:" + resumePath);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                this.cachedResume = reader.lines().collect(Collectors.joining("\n"));
                log.info("Successfully loaded master resume ({} characters).", cachedResume.length());
            }
        } catch (IOException e) {
            log.error("CRITICAL: Could not load master resume from path {}: {}", resumePath, e.getMessage());
            throw new RuntimeException("Failed to load master resume. The application cannot function without it.", e);
        }
    }

    /**
     * Returns the content of the master resume.
     * @return The resume text as a String.
     */
    public String getMasterResume() {
        return cachedResume;
    }
}
