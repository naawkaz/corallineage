package com.corallineage.backend.training;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingJobRepository extends JpaRepository<TrainingJob, Long> {

    Page<TrainingJob> findAllByOrderByInitiatedAtDesc(Pageable pageable);

    Page<TrainingJob> findByStatusOrderByInitiatedAtDesc(String status, Pageable pageable);
}
