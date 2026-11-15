# Improvements Plan: Local Job Agent Resume Generation System

## Executive Summary

This document outlines improvements to the existing job application automation pipeline, focusing on:
1. **Robust LaTeX template injection** with explicit markers
2. **PDF generation validation** with page count verification
3. **Improved AI prompts** for resume tailoring
4. **Comprehensive error handling** across the pipeline
5. **Thorough test coverage** without external dependencies

---

## Analysis of Existing Implementation

### Current State Assessment

| Component | Status | Issues Identified |
|-----------|--------|------------------|
| `TriageAgent` | ✅ Working well | None critical |
| `ResumeService` | ⚠️ Single-source load | Loads once at startup, cached globally |
| `PdfGeneratorService` | ⚠️ Needs improvement | No PDF page count verification, brittle regex replacement |
| `master-resume.tex` | ✅ Well-defined | Missing explicit injection markers |
| `application.properties` | ✅ Configurable | Hardcoded paths in PdfGeneratorService |
| Tests | ❌ None exist | No test coverage |

---

## Proposed Improvements

### 1. Add Explicit LaTeX Template Markers

**File:** `src/main/resources/master-resume.tex`

**Changes:**
- Add `% BEGIN TAILORED CONTENT` marker before Summary section
- Add `% END TAILORED CONTENT` marker after EXPERIENCE section
- Remove reliance on regex pattern matching for injection points

**Benefits:**
- Safer, more predictable content replacement
- Clear separation of static and dynamic content
- Easier maintenance and debugging

```latex
% --- BEGIN TAILORED CONTENT ---
% BEGIN TAILORED CONTENT
% (existing Summary section starts here)
...
% END TAILORED CONTENT
% --- END TAILORED CONTENT ---
```

---

### 2. Improve PdfGeneratorService

#### a) Add PDF Box dependency to pom.xml

**File:** `pom.xml`

**Add:**
```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>2.0.33</version>
</dependency>
```

#### b) Implement Page Count Validation

**File:** `src/main/java/com/localjobagent/service/PdfGeneratorService.java`

**New methods to add:**
- `boolean validatePdfPath(Path pdfPath)` - checks file exists and is not empty
- `int getPageCount(Path pdfPath)` - uses PDFBox to count pages
- `String getLaTeXCompileOutput(Process process)` - captures compiler output with proper logging

**Improved workflow in `generatePdf()`:**
```java
// 1. Compile
executeCommandWithPath("-interaction=nonstopmode", "-output-directory=" + absoluteOutputDir.toString(), absoluteTexPath);
boolean success = exitCode == 0;

if (!success) {
    log.error("LaTeX compilation failed: {}", pdflatexOutput);
    return null;
}

// 2. Validate PDF exists and has content
if (!validatePdfPath(pdfFile)) {
    log.error("Generated PDF is empty or missing");
    return null;
}

// 3. Verify page count
int pageCount = getPageCount(pdfFile);
if (pageCount > 1) {
    log.warning("PDF exceeds one page: {} pages. Attempting stricter tailoring...", pageCount);
    // Optionally retry with truncated content or tighter formatting instructions
}

log.info("PdfGenerator: successfully generated one-page PDF at {}", pdfFile.toAbsolutePath());
return pdfFile;
```

---

### 3. Replace Regex Injection with Template Marker-Based Approach

**File:** `src/main/java/com/localjobagent/service/PdfGeneratorService.java`

**Current brittle implementation:**
```java
// Uses regex that may break if content overlaps or changes structure
return template.replaceAll("(?s)%-----------SUMMARY-----------\\n.*\\resumeSubHeadingListEnd\\n\\n%-----------EXPERIENCE-----------------.*\\resumeSubHeadingListEnd", sanitizedContent);
```

**New reliable implementation:**
```java
public String injectContent(String template, String tailoredContent) {
    String markers = "% BEGIN TAILORED CONTENT\n";
    String endMarkers = "\n% END TAILORED CONTENT\n";
    
    if (!template.contains(markers) || !template.contains(endMarkers)) {
        log.error("Missing required template markers for content injection");
        throw new IllegalStateException(
            "Template is missing required markers. Add:\n  % BEGIN TAILORED CONTENT\n  % END TAILORED CONTENT"
        );
    }
    
    // Find the section to replace
    int start = template.indexOf(markers);
    int end = template.indexOf(endMarkers);
    
    if (start < 0 || end <= start) {
        log.error("Could not locate markers in template");
        throw new IllegalStateException("Failed to find marker positions in template");
    }
    
    return template.substring(0, start) + tailoredContent + endMarkers;
}
```

---

### 4. Improve AI Prompt for Resume Tailoring

**File:** `src/main/java/com/localjobagent/service/ResumeAdapterAgent.java`

**Current issues with prompt:**
- Mixed instructions about output format (LaTeX vs Markdown)
- No clear guidelines on factual integrity enforcement
- No fallback strategy if LLM produces invalid content

**New improved prompt structure:**

```java
private String buildPrompt(JobScraperService.ScrapedJob job, TriageAgent.TriageResult triage) {
    return """
            YOU ARE A PROFESSIONAL RESUME CONSULTANT SPECIALIZING IN LATEX DOCUMENTATION.
            
            YOUR ROLE:
            Transform a candidate's master resume into a targeted one-page version that 
            highlights their qualifications for a specific role while maintaining 
            complete factual integrity.
            
            ================================================================================
            SOURCE OF TRUTH: MASTER RESUME
            ================================================================================
            
            %s
            
            IMPORTANT CONSTRAINTS:
            1. NEVER INVENT EMPLOYERS, DATES, OR METRICS
            2. NEVER FABRICATE TECHNICAL SKILLS NOT ON THE RESUME
            3. NEVER EXAGGERATE EXPERIENCE TO MATCH A JOB DESCRIPTION
            4. PRIORITIZE CONTENT RELEVANT TO THE TARGET ROLE
            5. REMOVE IRRELEVANT PROJECTS AND SKILLS FOR THIS SPECIFIC APPLICATION
            
            ================================================================================
            TARGET OPPORTUNITY
            ================================================================================
            
            JOB TITLE: %s
            
            KEY REQUIREMENTS:
            %s (first 800 characters)
            
            ================================================================================
            FIT ANALYSIS
            ================================================================================
            
            Match Score: %d/100
            Reasoning: %s
            
            ================================================================================
            OUTPUT FORMAT REQUIREMENTS
            ================================================================================
            
            IMPORTANT: Output MUST be valid LaTeX only.
            
            1. Start with \\begin{document} immediately
            2. End with \\end{document} at the end
            3. Use existing \\begin{itemize} environments for lists
            4. Maintain consistent formatting with the master template
            
            DO NOT output:
            - Markdown code fences (```latex)
            - Explanatory text outside of LaTeX commands
            - Comments or notes
            
            If you produce content that requires reformatting, do so using the 
            existing \\begin{itemize} construct from the resume template.
            
            ================================================================================
            SPECIFIC TAILORING STRATEGY FOR %s ROLE
            ================================================================================
            
            1. SUMMARY SECTION:
               - Reframe the existing summary to highlight senior backend engineering
                 experience and relevant stack (Java, Spring Boot, Kafka, etc.)
               - Emphasize scale, reliability, and distributed systems experience
               - Keep concise (~2-3 sentences)
            
            2. EXPERIENCE SECTION:
               - Reorder sections by relevance to %s role
               - Rewrite bullet points to emphasize job-specific responsibilities
               - Quantify achievements where metrics exist in the resume
               - Remove less relevant projects entirely
            
            3. SKILLS SECTION:
               - Promote Java/Spring Boot/microservices skills
               - List relevant distributed systems tools (Kafka, Redis, etc.)
               - Remove less relevant technologies
            
            ================================================================================
            STRICT PROHIBITIONS
            ================================================================================
            
            DO NOT:
            - Invert employment dates or durations
            - Add companies or roles not listed in the resume
            - Fabricate performance metrics (%)
            - Create technical skills not evidenced in resume history
            - Change fundamental experience levels
            
            DO NOT OUTPUT:
            - Markdown code blocks
            - Plain text explanations before/after LaTeX
            - Any content outside the \\begin{document}...\end{document} block
            
            ================================================================================
            FAILURE HANDLING
            ================================================================================
            
            If your output produces a multi-page document when one page is required:
            1. Remove lower-value experience entries first (projects, minor roles)
            2. Condense bullet points without losing factual integrity
            3. Use tighter formatting that preserves readability
            4. NEVER shrink fonts to unreadable sizes
            
            Respond with ONLY valid LaTeX content, nothing else.
            """;
}
```

---

### 5. Add Robust Error Handling

**File:** `src/main/java/com/localjobagent/service/PdfGeneratorService.java`

**Add these error handlers:**
- Check for null or empty tailored content before compilation
- Validate input is actually LaTeX, not Markdown
- Ensure template markers exist before injection
- Track and report compilation errors with stderr output
- Verify PDF output file exists after compilation
- Check PDF page count and handle overflows gracefully

---

### 6. Add Comprehensive Test Coverage

**Location:** `src/test/java/com/localjobagent/service/`

#### Test Files to Create:

1. **PdfGeneratorServiceTest.java** - Unit and integration tests for PDF generation:
   - Template content replacement with valid markers
   - Missing or invalid template marker scenarios
   - LaTeX escaping and validation (non-LaTeX content injection)
   - Successful PDF generation (mocked)
   - LaTeX compilation failure handling (mocked)
   - PDF page-count validation (simulated)
   - Retry limits enforcement
   - Concurrent generation with isolated output files

2. **ResumeAdapterAgentTest.java** - Unit tests for tailoring:
   - Content truncation and length management
   - Job-specific relevance highlighting simulation
   - Invalid resume handling
   - Empty input rejection
   - Output validation checks

3. **TriageAgentTest.java** - Validation tests:
   - Score calculation verification
   - Status validation against allowed sets
   - Match decision logic correctness
   - Error case handling

4. **ResumeServiceTest.java** - Service tests:
   - Resume loading with valid path
   - Resume loading failure scenarios
   - Null safety and edge cases

---

## Implementation Order

### Phase 1: Foundation (Critical)
1. Add explicit template markers to `master-resume.tex`
2. Update `PdfGeneratorService.injectContent()` to use markers instead of regex
3. Add PDFBox dependency to `pom.xml`
4. Implement page count validation logic

### Phase 2: Improvement (Important)
1. Rewrite `ResumeAdapterAgent.buildPrompt()` with improved prompt structure
2. Add LaTeX input validation before injection
3. Improve error handling throughout the pipeline
4. Document configuration paths in application properties

### Phase 3: Reliability (Should Have)
1. Implement comprehensive error cases handling
2. Add configurable timeout for PDF compilation
3. Add retry limit tracking
4. Ensure concurrent request isolation with unique output files

### Phase 4: Verification (Must Have)
1. Create test suite covering all critical behaviors
2. Run and verify all tests pass locally
3. Document any environment prerequisites for integration testing

---

## Risk Assessment

| Change | Impact | Mitigation |
|--------|--------|------------|
| Template marker addition | Low | Safe, additive change |
| PdfGenerator rewrite | Medium | Preserve existing functionality during testing |
| PDFBox dependency | Medium | Evaluate necessity; use current approach if not needed |
| Test additions | Low | Isolated from runtime code |

---

## Definition of Complete

The following conditions must be met:

1. ✅ Master resume uses explicit `% BEGIN/END TAILORED CONTENT` markers
2. ✅ PdfGeneratorService validates PDF page count before returning success
3. ✅ Generated LaTeX content is validated as valid before injection
4. ✅ Error handling covers all specified failure modes in requirements
5. ✅ Unit tests exist and pass without external dependencies

---

## Files Changed Summary

| File | Action | Reason |
|------|--------|--------|
| `master-resume.tex` | Add markers | Safer content injection |
| `PdfGeneratorService.java` | Rewrite injectContent/validate | Reliable replacement |
| `pom.xml` | Add PDFBox | Page count validation capability |
| `ResumeAdapterAgent.java` | Improve prompt structure | Better output control |
| `application.properties` | Optional: config paths | More flexibility |
| *New test files* | Create coverage | Test required behaviors |
