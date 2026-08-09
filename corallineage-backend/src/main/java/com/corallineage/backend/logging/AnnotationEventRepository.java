package com.corallineage.backend.logging;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnotationEventRepository extends JpaRepository<AnnotationEvent, Long> {
}
