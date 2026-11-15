package com.localjobagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.nio.file.Path;

/**
 * EMAIL SERVICE
 *
 * Sends the final tailored PDF resume and job link to the user's email.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends a professional email with the PDF attached.
     *
     * @param jobTitle    The title of the job
     * @param jobLink     Link to the job posting
     * @param pdfPath     Path to the generated PDF file
     */
    public void sendTailoredResume(String jobTitle, String jobLink, Path pdfPath) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(fromEmail);
            helper.setTo(fromEmail); // Sending to self
            helper.setSubject("🚀 Tailored Resume: " + jobTitle);

            String body = String.format(
                "Hello,\n\nYour tailored resume for %s is ready!\n\n" +
                "Job Link: %s\n\n" +
                "Please find the PDF attached. Good luck with the application!",
                jobTitle, jobLink);

            helper.setText(body);
            helper.addAttachment("Tailored_Resume.pdf", new FileSystemResource(pdfPath.toFile()));

            mailSender.send(message);
            log.info("EmailService: successfully sent tailored resume for {}", jobTitle);
        } catch (Exception e) {
            log.error("EmailService: failed to send email - {}", e.getMessage());
        }
    }
}
