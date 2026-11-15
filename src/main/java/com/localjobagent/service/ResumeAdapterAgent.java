package com.localjobagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AGENT 3 - RESUME ADAPTER
 * <p>
 * Takes a job that passed triage and rewrites the Master Resume into a
 * tailored LaTeX version that highlights relevant keywords.
 */
public class ResumeAdapterAgent {

    private static final Logger log = LoggerFactory.getLogger(ResumeAdapterAgent.class);

    private final OllamaChatModel chatModel;
    private final ResumeService resumeService;

    public ResumeAdapterAgent(OllamaChatModel chatModel, ResumeService resumeService) {
        this.chatModel = chatModel;
        this.resumeService = resumeService;
    }

    /**
     * Produces a tailored LaTeX resume for the given job.
     *
     * @return LaTeX code, or empty string if the local model failed
     */
    public String tailorResume(JobScraperService.ScrapedJob job, TriageAgent.TriageResult triage) {
        log.info("ResumeAdapterAgent: ENTERING tailorResume for '{}'", job.title());
        String promptText = """
                You are a professional LaTeX expert.
                
                MASTER TEMPLATE:
                {{MASTER_RESUME}}
                
                JOB TITLE: {{JOB_TITLE}}
                JOB DESCRIPTION: {{JOB_DESCRIPTION}}
                FIT: {{FIT_SCORE}}/100 - {{FIT_REASONING}}
                
                STRICT TAILORING STRATEGY:
                1. RESPONSIBILITY ALIGNMENT:
                   Rewrite bullets to demonstrate responsibilities.
                
                2. KEYWORD INTEGRATION:
                   Integrate technologies into achievement bullets.
                
                3. IMPACT PRIORITIZATION:
                   Highlight quantitative metrics (%, $, time, scale).
                
                4. FACTUAL INTEGRITY:
                   Never invent technologies, titles, or achievements.
                
                STRICT OUTPUT RULES:
                1. Output valid LaTeX only.
                2. Start with \\begin{document}.
                3. End with \\end{document}.
                """;

        promptText = promptText
                .replace("{{MASTER_RESUME}}", resumeService.getMasterResume())
                .replace("{{JOB_TITLE}}", job.title())
                .replace("{{JOB_DESCRIPTION}}", truncate(job.description(), 3000))
                .replace("{{FIT_SCORE}}", String.valueOf(triage.getScore()))
                .replace("{{FIT_REASONING}}", triage.getReasoning());

        try {
            ChatResponse response = chatModel.call(new Prompt(promptText));
            String latex = response.getResult().getOutput().getText().trim();

            log.info("RAW LLM OUTPUT for {}:\\n{}\\n------------------", job.title(), latex);

            // Aggressively strip markdown code fences
            if (latex.contains("```")) {
                latex = latex.replaceAll("(?s)^.*?```(?:latex)?\\n", "");
                latex = latex.replaceAll("```$", "");
            }

            // POST-PROCESSING GUARD: Fix AI's habit of using dashes for bullets
            String[] lines = latex.split("\\n");
            StringBuilder fixedLatex = new StringBuilder();
            for (String line : lines) {
                String trimmed = line.trim();

                // 1. Handle the "Tech Stack" special case first (regardless of leading char)
                if (trimmed.toLowerCase().contains("tech stack") && !trimmed.contains("\\resumeItemTech")) {
                    // Remove leading dashes/bullets and "Tech Stack:" labels
                    String content = trimmed.replaceAll("^[\\s]*[\\–\\-\\u2013\\u2014]*", "")
                            .replaceAll("(?i)^Tech Stack\\s*:\\s*", "")
                            .trim();
                    fixedLatex.append("    \\resumeItemTech{Tech Stack}{").append(content).append("}\n");
                }
                // 2. Handle regular bullets (leading dashes, bullet points, or just plain text in lists)
                else if ((trimmed.startsWith("–") || trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.startsWith("·")) && !trimmed.contains("\\resumeItem")) {
                    String content = trimmed.replaceAll("^[\\s]*[\\–\\-\\u2013\\u2014\\u2022\\u00b7]*", "").trim();
                    fixedLatex.append("    \\resumeItem{").append(content).append("}\n");
                } else {
                    fixedLatex.append(line).append("\n");
                }
            }
            latex = fixedLatex.toString().trim();

            log.info("ResumeAdapterAgent: tailored LaTeX resume for '{}' ({} chars)",
                    job.title(), latex.length());
            return latex;
        } catch (Exception e) {
            log.error("ResumeAdapterAgent: failed for '{}' - {}", job.title(), e.getMessage());
            return "";
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...[truncated]";
    }
}
