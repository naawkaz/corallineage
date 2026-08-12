package com.corallineage.backend.reporting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReportMetadataRepository extends JpaRepository<ReportMetadata, Long> {

    Optional<ReportMetadata> findByReportId(String reportId);

    long countByReportIdStartingWith(String prefix);
}
