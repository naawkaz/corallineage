package com.corallineage.backend.ontology;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirtyAnnotationRepository extends JpaRepository<DirtyAnnotation, Long> {

    Page<DirtyAnnotation> findByOntologyChangeId(Long ontologyChangeId, Pageable pageable);

    Page<DirtyAnnotation> findByImageId(Long imageId, Pageable pageable);

    Page<DirtyAnnotation> findByOntologyChangeIdAndImageId(Long ontologyChangeId, Long imageId, Pageable pageable);

    long countByOntologyChangeId(Long ontologyChangeId);

    long countByOntologyChangeIdAndResolvedAtIsNotNull(Long ontologyChangeId);
}
