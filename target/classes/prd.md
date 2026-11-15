# Product Requirements Document (PRD): Local Job Agent

## 1. Overview
The **Local Job Agent** is an autonomous, agentic pipeline designed to streamline the job application process for software engineers. It replaces manual searching and tailoring by combining web scraping, LLM-based triaging, and automated resume customization.

The system operates entirely locally (using Ollama) to ensure privacy and eliminate API costs, providing a "filter" that only presents the user with high-probability job matches.

## 2. Target User
*   **Role:** Senior Software Engineer.
*   **Profile:** Experienced in Java, Spring Boot, and Fintech.
*   **Needs:** Seeking roles that offer relocation and require 6+ years of experience.

## 3. System Architecture & Workflow
The application implements a linear multi-agent pipeline coordinated by the `JobWorkflowOrchestrator`:

### Pipeline Stages:
1.  **Job Scraper Service (`JobScraperService`)**
    *   **Input:** Target URL and CSS selectors (configured in `application.properties`).
    *   **Action:** Scrapes job listings (Title, Company, Description, Link) using Jsoup.
    *   **Output:** A list of `ScrapedJob` objects.

2.  **Triage Agent (`TriageAgent`)**
    *   **Input:** Scraped Job + Master Resume.
    *   **Action:** Uses a local LLM (Ollama) to evaluate fit based on a strict prompt.
    *   **Logic:** Assigns a fit score (0-100). Only jobs scoring above a defined threshold (e.g., 75%) proceed.
    *   **Output:** `TriageResult` (match boolean, score, reasoning).

3.  **Resume Adapter Agent (`ResumeAdapterAgent`)**
    *   **Input:** Job details + Triage reasoning.
    *   **Action:** Tailors the Master Resume to highlight specific skills and experiences that align with the job description.
    *   **Output:** A tailored resume in Markdown format.

4.  **Persistence Layer**
    *   **Technology:** Spring Data JPA with MySQL.
    *   **Action:** Stores tailored applications with a status of `PENDING_REVIEW`.
    *   **De-duplication:** Ensures the same job link is not processed multiple times.

## 4. Functional Requirements

### 4.1 Automation
*   **Manual Trigger:** User can start the workflow via the Dashboard UI.
*   **Scheduled Trigger:** The system runs automatically on a cron schedule (default: every hour).

### 4.2 Filtering & Quality
*   **Strict Triage:** The system must act as a "gatekeeper," filtering out jobs that do not match the seniority or relocation requirements.
*   **Audit Trail:** The `TriageAgent` must provide short reasoning for why a job was matched or rejected.

### 4.3 User Interface
*   **Dashboard:** A Thymeleaf-based web interface to monitor the pipeline, view tailored resumes, and trigger runs.

## 5. Technical Stack
*   **Framework:** Spring Boot 3.4.x
*   **Language:** Java 17
*   **AI Engine:** Spring AI + Ollama (Llama 3)
*   **Database:** MySQL (via Hibernate/JPA)
*   **Scraping:** Jsoup
*   **Frontend:** Thymeleaf + HTML/CSS

## 6. Future Enhancements (Roadmap)
*   **Advanced Search:** Integration of Boolean search strings for LinkedIn.
*   **Application Tracking:** Ability to mark applications as "Applied" or "Interviewing" via the dashboard.
*   **Email Integration:** Automatic notifications when a high-match (90%+) job is found.
*   **PDF Generation:** Converting the tailored Markdown resumes into professional PDFs.
