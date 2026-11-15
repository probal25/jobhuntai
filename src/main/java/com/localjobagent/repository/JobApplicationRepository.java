package com.localjobagent.repository;

import com.localjobagent.entity.ApplicationStatus;
import com.localjobagent.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository. Implementation is generated at runtime.
 */
@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    /** Dashboard query: everything awaiting human review. */
    List<JobApplication> findByStatusOrderByMatchScoreDesc(ApplicationStatus status);

    /** De-duplication guard: skip jobs we have already stored. */
    boolean existsByLink(String link);

    long countByStatus(ApplicationStatus status);
}
