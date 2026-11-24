# LocalJobAgent

A 100% local agentic job-hunting workflow built with **Spring Boot**, **Spring AI**, **Ollama**, **Playwright**, and **Jsoup**. This system automates the entire job application pipeline from scraping to resume tailoring and PDF generation.

---

## 🏗️ System Architecture

```
┌─────────────┐     ┌─────────────┐     ┌──────────────────┐     ┌──────────┐
│  Job        │────>│   Triage    │────>│ Resume Adapter   │────>│ PDF     │
│  Scraper    │     │   Agent     │     │   Agent          │     │ Gen     │
└─────────────┘     └─────────────┘     └──────────────────┘     └──────────┘
      ↓                      ↓                    ↓                  ↓
 Ollama LLM              Ollama LLM           Ollama LLM         pdflatex
 (local model)          (local model)        (local model)      (local)
      ↓                      ↓                    ↓                  ↓
    ┌──────────┐     ┌──────────┐     ┌──────────────────┐     ┌─────────┐
│  Job Scraper │     │   Score   │     │ Tailored Resume  │     │PDF File│
│  Playwright  │     │  Agent    │     │  (LaTeX)        │     │ Generator│
│  Jsoup        │     │   Agent   │     │                 │     │ Service │
└──────────────┘     └──────────┘     └──────────────────┘     └─────────┘
      │                      │                    │                  │
      └──────────────────────┘                    └─────────────────┘
                         ↓
                 ┌──────────────┐
                 │  H2 Database  │
                 │   (JPA)       │
                 └──────────────┘
```

---

## 🔄 Multi-Agent Workflow Pipeline

### **Agent 1: Job Scraper** (`JobScraperService`)

**Technology Stack:**
- **Playwright** - Headless browser automation for JS-heavy sites (LinkedIn, Indeed)
- **Jsoup** - HTML parsing and extraction
- **Spring Boot** - Request orchestration

**Capabilities:**
- Bypass LinkedIn login walls using injected session cookies
- Navigate job boards as authenticated users
- Extract job postings with title, company, description, and direct links
- Handle JavaScript-rendered content
- Rate-limit requests (randomized delays between scrapes)

**Flow:**
1. Initialize Playwright browser (headless Chrome)
2. Load session cookies from `linkedin-cookies.json`
3. Navigate to target job board URLs
4. Wait for job cards DOM to load
5. Extract structured job data using CSS selectors
6. Store scraped jobs in memory

---

### **Agent 2: Triage Agent** (`TriageAgent`)

**Technology Stack:**
- **Ollama LLM** (local model, e.g., `llama3`)
- **Spring AI** - LLM orchestration layer
- **Jackson** - JSON parsing and serialization

**Purpose:** Evaluate scraped jobs against the candidate's master resume using a local LLM.

**Evaluation Criteria:**

| Criterion | Weight | Description |
|-----------|--------|-------------|
| Technical Skills | 35 pts | Mandatory vs preferred tech stack matching |
| Engineering Responsibility | 25 pts | Domain knowledge and ownership alignment |
| Seniority & Experience | 15 pts | Match against candidate's experience level |
| Domain & Industry | 10 pts | Fintech, payment systems, distributed systems |
| Preferred Skills | 10 pts | Optional tech stack overlap |
| Resume Evidence | 5 pts | Quality and clarity of claims |

**Scoring Threshold:**
- **Match:** Score > 60% with location/visa/seniority eligibility
- **Skip:** Score ≤ 60% or any disqualifying factor

**Eligibility Checks:**

| Check | Status Options | Required for Match |
|-------|----------------|-------------------|
| Location | ELIGIBLE, INELIGIBLE, UNKNOWN | Must be ELIGIBLE |
| Visa Sponsorship | CONFIRMED, POSSIBLE, UNKNOWN, NOT_AVAILABLE | CONFIRMED/POSSIBLE or UNKNOWN |
| Seniority | STRONG, GOOD, POSSIBLE, MISMATCH | STRONG/GOOD/POSSIBLE |

**Output Format:** Structured JSON with all scores and reasoning

```json
{
  "match": true,
  "score": 85,
  "locationStatus": "ELIGIBLE",
  "visaStatus": "CONFIRMED",
  "seniorityStatus": "STRONG",
  "technicalScore": 30,
  "reasoning": "Strong backend engineering experience with relevant distributed systems."
}
```

---

### **Agent 3: Resume Adapter Agent** (`ResumeAdapterAgent`)

**Technology Stack:**
- **Ollama LLM** (local model)
- **Spring AI** - Prompt engineering and output formatting

**Purpose:** Take a job that passed triage and rewrite the Master Resume into a tailored LaTeX version.

**Tailoring Strategy:**

1. **Responsibility Alignment** - Rewrite bullets to demonstrate relevant responsibilities
2. **Keyword Integration** - Integrate job technologies into achievement bullets
3. **Impact Prioritization** - Highlight quantitative metrics (%, time, scale)
4. **Factual Integrity** - Never invent technologies or titles

**Output Processing:**
- Parse LLM response and strip markdown code fences
- Post-process bullet point formatting for LaTeX compatibility
- Ensure valid LaTeX structure with `\begin{document}` and `\end{document}`

---

### **PDF Generator Service** (`PdfGeneratorService`)

**Technology Stack:**
- **LaTeX (pdflatex)** - Professional document generation
- **Java ProcessBuilder** - Shell command execution

**Workflow:**
1. Read master resume LaTeX template from `src/main/resources/master-resume.tex`
2. Inject tailored content from `ResumeAdapterAgent` output
3. Sanitize LaTeX special characters (`&`, `%`, `$`, etc.)
4. Compile twice to resolve references and formatting issues
5. Clean up intermediate `.log`, `.aux`, `.out` files
6. Generate final PDF in `generated_resumes/` directory

---

## 📊 Data Persistence (JPA/H2)

| Entity | Description | Field | Type |
|--------|-------------|-------|------|
| JobApplication | Scraped job with pipeline results | id | @Id, @GeneratedValue(IDENTITY) |
| | | title | String | NOT NULL |
| | | company | String | NOT NULL |
| | | description | String (8000 chars) | |
| | | link | String (768 chars) | UNIQUE, NOT NULL |
| | | matchScore | int | 0-100 from Triage Agent |
| | | triageReasoning | Text | From Triage Agent |
| | | tailoredResume | LongText | Tailored LaTeX from Adapter Agent |
| | | status | ApplicationStatus ENUM | PENDING_REVIEW/REJECTED/PREVIOUSLY_SCORRED |

**Indexes:**
- `idx_status` - Status column
- `idx_link` - Unique constraint on job link (de-duplication)

---

## 🛠️ Technologies Used

| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| **Framework** | Spring Boot | 3.4.12 | REST APIs, Web services |
| **AI Orchestration** | Spring AI | 1.0.0 | LLM integration layer |
| **Local Model** | Ollama | n/a | Local llama3 inference |
| **Browser Automation** | Playwright | 1.49.0 | JS-heavy site scraping |
| **HTML Parsing** | Jsoup | 1.17.2 | Server-side HTML extraction |
| **Database** | MySQL / H2 | n/a | JPA storage |
| **Template Engine** | Thymeleaf | n/a | Dashboard rendering |
| **Mail** | Spring Mail | n/a | Resume notification emails |
| **Validation** | Bean Validation | n/a | @Valid, @NotNull annotations |

---

## 🚀 Project Flow Overview

```
1. APPLICATION STARTUP
   ↓
2. JOB SCRAPING (Playwright + Jsoup)
   → Scrapes jobs from configured endpoints
   → Uses session cookies to bypass auth walls
   → Stores in memory
   ↓
3. DE-DUPLICATION CHECK
   → Query JPA: "Does this link already exist?"
   → Skip if duplicate found
   ↓
4. TRIAGE EVALUATION (Local LLM)
   → Feed master resume + scraped job to Ollama
   → Evaluate across 6 weighted criteria
   → Return structured JSON with scores
   → Java enforces eligibility rules on match decision
   ↓
5. JOB DECISION
   → Score > 60 AND eligible = PASS to Adapter Agent
   → Otherwise: SKIP (log reason)
   ↓
6. RESUME TAILORING (Local LLM)
   → Input: Tailored job + triage reasoning
   → Output: LaTeX resume format
   → Post-process for LaTeX compatibility
   ↓
7. PERSISTENCE
   → Create JobApplication entity
   → Set all pipeline outputs as fields
   → Save to H2 database
   ↓
8. PDF GENERATION (pdflatex)
   → Inject tailored content into master template
   → Compile twice for reference resolution
   → Clean up intermediate files
   → Store PDF in generated_resumes/
```

---

## 📁 Project Structure

```
local-job-agent/
├── src/
│   ├── main/
│   │   ├── java/com/localjobagent/
│   │   │   ├── entity/        # JPA entities (JobApplication, ApplicationStatus)
│   │   │   ├── repository/     # Spring Data JPA repositories
│   │   │   ├── service/        # Business logic services (Agents & helpers)
│   │   │   └── workflow/       # Pipeline orchestration
│   │   │   └── controller/     # REST API endpoints
│   │   │   └── resources/      # Master resume template, cookies config
│   │   └── test/               # Unit tests
│   └── test/                   # Test resources
├── ai-plan/                    # Agent planning configs
├── ai-summary/                 # AI summaries
├── ai-task/                    # Task definitions
├── generated_resumes/          # Output PDFs (ignored by git)
├── target/                     # Build artifacts (ignored by git)
├── pom.xml                    # Maven build configuration
└── README.md                  # This file
```

---

## 🔧 Configuration Requirements

### Prerequisites

1. **Java 17+** - Required for Spring Boot 3.x
   ```bash
   java -version
   ```

2. **Ollama Server** - Local LLM inference
   ```bash
   ollama serve
   ollama pull llama3
   ```

3. **Playwright** - Browser automation library
   ```bash
   brew install --quiet playwright @playwright-community/playwright  # macOS
   # or use npm install @playwright/community on Linux/Windows
   ```

4. **TeX Distribution** - PDF generation
   ```bash
   # macOS
   brew install --quiet texlive
   
   # Ubuntu/Debian
   sudo apt-get install texlive-xetex
   ```

### Application Configuration (`application.properties`)

```properties
# Ollama Model Configuration
spring.ai.ollama.model=default

# Scaper Configuration
scraper.endpoint-list=<TARGET_URLS>
scraper.container-selector=.job-card
scraper.title-selector=.job-title
scraper.description-selector=.job-description
scraper.link-selector=a.job-link

# DB Configuration (H2 for dev, MySQL for prod)
spring.datasource.url=jdbc:h2:./generated_resumes
spring.datasource.driverClassName=org.h2.Driver
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect

# Server Configuration
server.port=8080
```

---

## 🎯 Key Technical Highlights

### **Local LLM Integration**
- No cloud API keys or external dependencies
- Runs entirely on-premise with local Ollama instance
- Private resume processing (no data leakage)

### **Hybrid Scrape Approach**
- Playwright for complex JavaScript sites (LinkedIn)
- Jsoup for simple static pages (Indeed, Monster)
- Session cookie injection for authenticated access

### **Deterministic Scoring**
- LLM provides scores; Java enforces rules
- Prevents hallucinated matches
- Transparent audit trail with reasoning strings

### **LaTeX Pipeline**
- Master template ensures consistent formatting
- Content injection preserves structure
- Post-processing handles AI output artifacts

---

## 📚 Files Referenced

| File | Purpose |
|------|---------|
| `JobWorkflowOrchestrator.java` | Main pipeline coordinator |
| `TriageAgent.java` | LLM-based job evaluation with scoring logic |
| `ResumeAdapterAgent.java` | Tailores resume to specific job requirements |
| `PdfGeneratorService.java` | Compiles LaTeX templates to PDF |
| `JobScraperService.java` | Scrapes jobs using Playwright/Jsoup |
| `master-resume.tex` | Master resume template for content injection |
| `linkedin-cookies.json` | Session cookies for LinkedIn scraping |

---

## 🔐 Privacy & Security Notes

- **No cloud LLM calls** - All inference runs locally on Ollama
- **No external API keys** - Pure local deployment
- **Session cookies** - Loaded from local `linkedin-cookies.json` (never sent externally)
- **PDF output location** - Stored in `generated_resumes/` directory

---

## 🤖 Deployment Options

### Development (H2 Database)
```bash
mvn spring-boot:run
# Runs on http://localhost:8080
```

### Production (MySQL Database)
```bash
# Update application.properties with MySQL URL
mvn spring-boot:run
```

---

## 📄 License & Attribution

Co-Authored-By: Claude Code <noreply@anthropic.com>
