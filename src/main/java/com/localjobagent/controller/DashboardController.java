package com.localjobagent.controller;

import com.localjobagent.entity.ApplicationStatus;
import com.localjobagent.entity.JobApplication;
import com.localjobagent.repository.JobApplicationRepository;
import com.localjobagent.workflow.JobWorkflowOrchestrator;
import com.localjobagent.service.PdfGeneratorService;
import com.localjobagent.service.EmailService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Thymeleaf dashboard controller.
 *
 *   GET  /                      -> review queue (PENDING_REVIEW)
 *   POST /run-workflow          -> trigger the agent pipeline manually
 *   POST /decision/{id}         -> approve or reject a job
 *   GET  /?status=APPROVED      -> filter by any status
 */
@Controller
public class DashboardController {

    private final JobApplicationRepository repository;
    private final JobWorkflowOrchestrator orchestrator;
    private final PdfGeneratorService pdfGenerator;
    private final EmailService emailService;

    public DashboardController(JobApplicationRepository repository,
                               JobWorkflowOrchestrator orchestrator,
                               PdfGeneratorService pdfGenerator,
                               EmailService emailService) {
        this.repository = repository;
        this.orchestrator = orchestrator;
        this.pdfGenerator = pdfGenerator;
        this.emailService = emailService;
    }

    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) String status, Model model) {
        ApplicationStatus filter = parseStatus(status);
        List<JobApplication> jobs = repository.findByStatusOrderByMatchScoreDesc(filter);

        model.addAttribute("jobs", jobs);
        model.addAttribute("activeFilter", filter);
        model.addAttribute("pendingCount", repository.countByStatus(ApplicationStatus.PENDING_REVIEW));
        model.addAttribute("approvedCount", repository.countByStatus(ApplicationStatus.APPROVED));
        model.addAttribute("rejectedCount", repository.countByStatus(ApplicationStatus.REJECTED));
        return "index";
    }

    /** Manual trigger button for the pipeline. */
    @PostMapping("/run-workflow")
    public String runWorkflow(RedirectAttributes redirect) {
        orchestrator.runWorkflowAsync();
        redirect.addFlashAttribute("message", "Workflow started in background. Please refresh the page in a few minutes to see new jobs.");
        return "redirect:/";
    }

    /** Approve / Reject decision from the dashboard. */
    @PostMapping("/decision/{id}")
    public String decide(@PathVariable Long id,
                         @RequestParam String action,
                         RedirectAttributes redirect) {
        repository.findById(id).ifPresent(job -> {
            if ("approve".equalsIgnoreCase(action)) {
                // 1. Generate Professional PDF from LaTeX
                Path pdfPath = pdfGenerator.generatePdf(job.getTailoredResume());

                // 2. Send to Email if PDF was successful
                if (pdfPath != null) {
                    emailService.sendTailoredResume(job.getTitle(), job.getLink(), pdfPath);
                }

                job.setStatus(ApplicationStatus.APPROVED);
            } else if ("reject".equalsIgnoreCase(action)) {
                job.setStatus(ApplicationStatus.REJECTED);
            }
            job.setReviewedAt(LocalDateTime.now());
            repository.save(job);
        });
        redirect.addFlashAttribute("message",
                "Job #" + id + " marked as " + action.toUpperCase());
        return "redirect:/";
    }

    /** Transition job to next lifecycle stage (e.g., APPROVED -> APPLIED). */
    @PostMapping("/transition/{id}")
    public String transition(@PathVariable Long id,
                             @RequestParam ApplicationStatus nextStatus,
                             RedirectAttributes redirect) {
        repository.findById(id).ifPresent(job -> {
            job.setStatus(nextStatus);
            job.setReviewedAt(LocalDateTime.now());
            repository.save(job);
        });
        redirect.addFlashAttribute("message", "Job #" + id + " transitioned to " + nextStatus);
        return "redirect:/";
    }

    private ApplicationStatus parseStatus(String status) {
        if (status == null) return ApplicationStatus.PENDING_REVIEW;
        try {
            return ApplicationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ApplicationStatus.PENDING_REVIEW;
        }
    }
}
