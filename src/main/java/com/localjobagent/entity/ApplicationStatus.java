package com.localjobagent.entity;

/**
 * Lifecycle status of a scraped job application.
 */
public enum ApplicationStatus {
    PENDING_REVIEW,   // scraped + tailored resume generated, awaiting human decision
    APPROVED,         // human approved -> ready to apply
    APPLIED,          // application submitted to the employer
    INTERVIEWING,     // currently in the interview process
    OFFERED,          // received a job offer
    REJECTED          // human rejected or employer rejected
}
