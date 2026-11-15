package com.localjobagent.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing one scraped job listing and everything
 * the multi-agent pipeline produced for it.
 */
@Entity
@Table(name = "job_applications", indexes = {
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_link", columnList = "link", unique = true) // de-duplication
})
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String company;

    @Column(length = 8000)
    private String description;

    @Column(nullable = false, unique = true, length = 768)
    private String link;

    /** Fit score 0-100 produced by the Triage Agent. */
    private int matchScore;

    /** Short explanation from the Triage Agent (audit trail). */
    @Column(columnDefinition = "TEXT")
    private String triageReasoning;

    /** Tailored Markdown resume produced by the Resume Adapter Agent. */
    @Column(columnDefinition = "LONGTEXT")
    private String tailoredResume;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.PENDING_REVIEW;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime reviewedAt;

    // ---------- Constructors ----------
    public JobApplication() {}

    public JobApplication(String title, String company, String description, String link) {
        this.title = title;
        this.company = company;
        this.description = description;
        this.link = link;
    }

    // ---------- Getters / Setters ----------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public int getMatchScore() { return matchScore; }
    public void setMatchScore(int matchScore) { this.matchScore = matchScore; }

    public String getTriageReasoning() { return triageReasoning; }
    public void setTriageReasoning(String triageReasoning) { this.triageReasoning = triageReasoning; }

    public String getTailoredResume() { return tailoredResume; }
    public void setTailoredResume(String tailoredResume) { this.tailoredResume = tailoredResume; }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
