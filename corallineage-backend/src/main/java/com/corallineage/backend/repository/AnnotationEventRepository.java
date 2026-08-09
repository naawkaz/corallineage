package com.corallineage.backend.repository;

import com.corallineage.backend.entity.AnnotationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnotationEventRepository extends JpaRepository<AnnotationEvent, Long> {
}
