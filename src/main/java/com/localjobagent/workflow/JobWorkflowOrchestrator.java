package com.localjobagent.workflow;

import com.localjobagent.entity.ApplicationStatus;
import com.localjobagent.entity.JobApplication;
import com.localjobagent.repository.JobApplicationRepository;
import com.localjobagent.service.JobScraperService;
import com.localjobagent.service.ResumeAdapterAgent;
import com.localjobagent.service.TriageAgent;
import com.localjobagent.service.ResumeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * WORKFLOW ORCHESTRATOR
 * <p>
 * Coordinates the multi-agent pipeline:
 * <p>
 * [Scraper Agent]  --jobs-->  [Triage Agent] --match?--> [Resume Adapter Agent]
 * |                                                        |
 * +------------------>  [H2 Storage via JPA]  <------------+
 * <p>
 * Triggered manually via the dashboard button, or automatically on a cron.
 */
@Service
public class JobWorkflowOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(JobWorkflowOrchestrator.class);

    private final JobScraperService scraper;
    private final TriageAgent triageAgent;
    private final JobApplicationRepository repository;
    private final ResumeAdapterAgent resumeAdapter;

    public JobWorkflowOrchestrator(JobScraperService scraper,
                                   TriageAgent triageAgent,
                                   JobApplicationRepository repository,
                                   ResumeService resumeService,
                                   OllamaChatModel chatModel) {
        this.scraper = scraper;
        this.triageAgent = triageAgent;
        this.repository = repository;
        this.resumeAdapter = new ResumeAdapterAgent(chatModel, resumeService);
    }

    /**
     * Asynchronous entry point for the pipeline.
     * Uses CompletableFuture to run the process in a separate thread.
     */
    @Async
    public CompletableFuture<Void> runWorkflowAsync() {
        log.info("=== Async Workflow run started ===");
        try {
            runWorkflow();
        } catch (Exception e) {
            log.error("Critical error during async workflow: {}", e.getMessage());
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Core logic of the pipeline.
     * Note: @Transactional here ensures database consistency per run.
     */
    @Transactional
    public String runWorkflow() {
        log.info("=== Workflow run started ===");

        List<JobScraperService.ScrapedJob> scrapedJobs = scraper.scrapeJobs();
        int newJobs = 0, triaged = 0, tailored = 0, duplicates = 0, failed = 0;

        for (JobScraperService.ScrapedJob job : scrapedJobs) {

            // --- De-duplication: unique constraint on link ---------------
            if (repository.existsByLink(job.link())) {
                duplicates++;
                continue;
            }

            // --- Step 2: Triage Agent -------------------------------------
            TriageAgent.TriageResult triage = triageAgent.evaluate(job);
            triaged++;

            if (!triage.isMatch()) {
                log.info("Workflow: '{}' scored {}% - below threshold, skipping",
                        job.title(), triage.getScore());
                continue;
            }

            // --- Step 3: Resume Adapter Agent -----------------------------
            String markdown = resumeAdapter.tailorResume(job, triage);
            if (markdown.isBlank()) {
                failed++;
                continue;
            }
            tailored++;

            // --- Step 4: Persist -------------------------------------------
            log.info("Creating entity for: {}", job.title());
            JobApplication entity = new JobApplication();
            entity.setTitle(job.title());
            entity.setCompany(job.company());
            entity.setDescription(job.description());
            entity.setLink(job.link());

            entity.setMatchScore(triage.getScore());
            entity.setTriageReasoning(triage.getReasoning());
            entity.setTailoredResume(markdown);
            entity.setStatus(ApplicationStatus.PENDING_REVIEW);

            log.info("Attempting to save entity to database for: {}", job.title());
            repository.save(entity);
            newJobs++;
        }

        String summary = "Workflow complete: %d scraped, %d new saved, %d tailored, %d below threshold, %d duplicates, %d failures"
                .formatted(scrapedJobs.size(), newJobs, tailored,
                        triaged - tailored - failed, duplicates, failed);
        log.info("=== {} ===", summary);
        return summary;
    }

//    @Scheduled(cron = "0 0 * * * *")
//    public void scheduledRun() {
//        log.info("Cron trigger fired - starting scheduled workflow run");
//        runWorkflow();
//    }
}
