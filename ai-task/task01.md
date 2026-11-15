You are a senior Java Spring Boot engineer with expertise in Spring AI, Ollama, LaTeX, and PDF generation.

I am building a local AI-powered job application agent using Java, Spring Boot, Spring AI, and Ollama. I already have a master resume written in LaTeX and an existing PDF generation service.

Your task is to inspect my existing codebase and implement the following improvements while preserving the current architecture wherever possible.

## 1. First, Understand the Existing Codebase

Before making changes, inspect these components:

- `TriageAgent`
- `ResumeService`
- `PdfGeneratorService`
- `master-resume.tex`
- `application.properties` or `application.yml`
- Existing Maven dependencies in `pom.xml`

Understand how the application currently:
1. Loads the master resume.
2. Analyzes job descriptions.
3. Determines whether a job matches my profile.
4. Generates tailored resume content.
5. Injects the generated content into the LaTeX template.
6. Compiles the LaTeX document into a PDF.

Do not assume the existing implementation is incorrect without inspecting it.

## 2. Preserve the Master Resume

My master resume already exists as a LaTeX file. Do not generate an entirely new resume template.

The master resume is the source of truth for my professional experience, technical skills, projects, education, and achievements.

The AI must never:
- Invent employers, job titles, responsibilities, or achievements.
- Fabricate metrics, performance improvements, or financial figures.
- Add technologies that are not supported by my master resume.
- Change employment dates or other factual information.
- Exaggerate my experience to match a job description.

The AI may:
- Reorder existing skills according to job relevance.
- Rewrite existing bullet points for clarity and relevance.
- Prioritize experience relevant to the target role.
- Remove irrelevant skills, projects, or bullet points.
- Shorten descriptions while preserving their factual meaning.

My professional background is primarily Senior Java/Spring Boot backend engineering and fintech/payment systems. Prioritize relevant experience in Java, Spring Boot, microservices, REST APIs, databases, messaging systems, distributed systems, payment integrations, reliability, and production operations when these are supported by the master resume and relevant to the target job.

## 3. Implement Resume Tailoring

Update or introduce a dedicated resume-tailoring component if necessary.

The tailoring component should receive:
- The complete master resume.
- The target job description.
- The job evaluation result, if available.

It should return structured, valid content suitable for insertion into my existing LaTeX template.

The generated resume must:
- Be tailored to the specific job description.
- Prioritize the most relevant and valuable experience.
- Remove irrelevant sections when appropriate.
- Keep important employment history and factual information accurate.
- Use concise, professional, ATS-friendly language.
- Avoid unnecessary repetition and generic statements.
- Target approximately 350–450 words when appropriate, adjusting for the existing template and actual content.
- Fit on exactly one A4 page when reasonably possible without making the font unreasonably small or the layout difficult to read.

Do not force the resume to contain 350–450 words if doing so would require inventing information or making the document unreadable.

### LaTeX Output Requirements

Inspect `master-resume.tex` before deciding how the AI should return its output.

Prefer returning only the content intended for an explicitly defined placeholder in the existing template.

If the current template contains clearly defined sections, preserve their existing formatting and commands.

The AI must not return:
- Markdown code fences.
- Explanations outside the generated content.
- A complete replacement document with `\documentclass` or `\begin{document}` unless the existing architecture specifically requires a complete document.

The generated content must be valid for the actual insertion point in the template.

## 4. Improve the LaTeX Template Integration

Inspect `PdfGeneratorService.injectContent()` and replace brittle content injection with a reliable approach.

If appropriate, add explicit markers to `master-resume.tex`, for example:

`% BEGIN TAILORED CONTENT`

`% END TAILORED CONTENT`

Use these markers to identify the replaceable portion of the document.

Requirements:
- Preserve the master template's preamble, document configuration, contact details, and other permanent sections.
- Replace only the designated dynamic content.
- Do not use fragile regular expressions to replace large LaTeX sections when explicit markers provide a safer solution.
- Validate that both markers exist and that they appear in the correct order.
- Fail with a descriptive exception if the template is invalid.
- Do not silently fall back to a completely different resume format.

### Important: Do Not Corrupt LaTeX

Inspect `sanitizeLatex()` carefully.

Escaping LaTeX characters is appropriate for plain text, but applying a plain-text escaping method to generated LaTeX will corrupt commands such as `\section`, `\textbf`, and `\begin{itemize}`.

Distinguish between:
1. Plain text that must be escaped before insertion.
2. Generated LaTeX that must be validated and inserted as LaTeX.

Do not blindly trust arbitrary model output as executable LaTeX. Validate the generated content and restrict it to the commands and structures required by the existing resume template. Avoid dangerous commands, external file inclusion, shell execution, or changes to the document preamble.

Preserve existing functionality unless a change is necessary.

## 5. Generate and Validate a One-Page PDF

Improve `PdfGeneratorService` so that it reliably generates and validates the PDF.

Current configuration includes:
- LaTeX template: `src/main/resources/master-resume.tex`
- Output directory: `generated_resumes/`
- PDF compiler: `/Library/TeX/texbin/pdflatex`

Inspect the existing implementation before modifying these paths.

Requirements:
- Preserve the configured compiler path unless configuration needs to be improved.
- Prefer configurable filesystem paths over hardcoded paths where practical.
- Ensure the output directory exists.
- Handle file paths safely.
- Run LaTeX compilation with an appropriate timeout.
- Capture compiler output and provide useful error messages when compilation fails.
- Verify that the generated PDF exists and is not empty.
- Do not return a successful result when compilation fails.
- Avoid unnecessary cleanup of files that are still needed.
- Keep generated files isolated by request or job identifier to prevent concurrent requests from overwriting one another.

### PDF Page Count

Use Apache PDFBox if the project does not already have a suitable PDF page-counting library.

After compilation:
1. Open the generated PDF.
2. Count the pages.
3. Accept the document only if it contains exactly one page.
4. If the document contains more than one page, attempt a limited correction using a stricter tailoring prompt or a controlled layout adjustment.
5. Limit the number of retries to avoid infinite loops.
6. If the document still does not fit, return a clear failure or an explicit indication that one-page generation was unsuccessful.

Do not solve overflow by arbitrarily shrinking the font to an unreadable size.

Choose a PDFBox version compatible with the project's Java version and existing dependencies. Do not upgrade unrelated dependencies.

## 6. Improve the AI Prompt

Create a dedicated, maintainable prompt for resume tailoring instead of mixing unrelated instructions into Java code.

The prompt should instruct the model to:
- Treat the master resume as the only source of professional facts.
- Extract the target job's most important requirements.
- Map those requirements to existing experience.
- Prioritize relevant content.
- Remove or shorten less relevant material.
- Preserve factual accuracy.
- Return only the required LaTeX content.
- Respect the existing template's structure.
- Produce concise content suitable for a one-page A4 resume.

If the first PDF exceeds one page, use a stricter follow-up instruction that prioritizes removing lower-value content rather than fabricating information or damaging the layout.

Keep the implementation compatible with the existing Spring AI and Ollama setup.

## 7. Preserve the Existing Job Evaluation Flow

Do not unnecessarily rewrite `TriageAgent` or the job-matching logic.

The job evaluation and resume tailoring responsibilities should remain distinct:

- `TriageAgent`: Determines how well a job matches my profile and provides the relevant evaluation.
- `ResumeService`: Loads and provides the master resume.
- Resume-tailoring component: Creates job-specific resume content.
- `PdfGeneratorService`: Injects the content into the template, compiles the PDF, and validates the output.

Follow the project's existing naming conventions and dependency-injection patterns.

Avoid duplicating responsibilities between services.

## 8. Error Handling and Reliability

Handle these cases clearly:
- Master resume cannot be loaded.
- Job description is missing or empty.
- AI returns empty content.
- AI returns Markdown instead of the expected content.
- AI returns invalid or malformed LaTeX.
- Required template markers are missing.
- LaTeX compilation fails.
- PDF generation times out.
- PDF output is missing or empty.
- Generated PDF has more than one page.
- Concurrent requests attempt to generate resumes simultaneously.

Do not swallow exceptions and return `null` without useful diagnostic information.

Use the project's existing error-handling conventions. If necessary, introduce a clear result type or meaningful exceptions.

Do not log API credentials, secrets, or the entire resume unnecessarily.

## 9. Configuration and Dependencies

Inspect the existing `pom.xml` and configuration files before adding anything.

- Reuse existing dependencies where possible.
- Add PDFBox only if necessary.
- Keep the project compatible with its current Java and Spring Boot versions.
- Preserve the existing local Ollama integration.
- Do not introduce paid APIs or external AI services.
- Do not replace Ollama with a cloud model.
- Avoid unnecessary architectural changes.

## 10. Testing Requirements

Add or update tests for the important behavior.

At minimum, cover:
- Template content replacement.
- Missing or invalid template markers.
- LaTeX escaping and validation.
- Empty AI output.
- Successful PDF generation.
- LaTeX compilation failure.
- PDF page-count validation.
- Retry limits when the PDF exceeds one page.
- Concurrent generation with separate output files.

Mock external processes or AI calls where appropriate so that unit tests do not require a running Ollama instance or a local LaTeX installation.

If integration tests require those tools, document the prerequisites.

## 11. Implementation Workflow

Follow this workflow:

1. Inspect the existing implementation and identify its actual problems.
2. Explain the proposed changes briefly.
3. Modify the existing code instead of replacing working components unnecessarily.
4. Update `master-resume.tex` only where necessary.
5. Implement reliable template injection and PDF validation.
6. Update the AI prompts and service integration.
7. Add or update relevant tests.
8. Run the available tests and build.
9. Report the files changed, the reasons for the changes, and any remaining limitations.

Do not stop after providing recommendations. Implement the changes directly in the codebase.

Do not rewrite the entire project, introduce unnecessary abstractions, or change unrelated business logic.

If an important implementation detail cannot be determined from the repository, identify the uncertainty before making a potentially destructive change.

## Definition of Done

The task is complete when:

- The existing master resume remains the source of truth.
- A job-specific resume can be generated without inventing professional facts.
- Only the intended template content is replaced.
- LaTeX commands are not corrupted by plain-text escaping.
- The PDF compiler's failures are reported clearly.
- The generated PDF is verified to contain exactly one page before being reported as successful.
- Retries are bounded and do not loop indefinitely.
- Existing job evaluation and Ollama integration continue to work.
- Relevant tests pass, or any environment-related testing limitations are clearly documented.

Start by inspecting the repository, then implement the changes.