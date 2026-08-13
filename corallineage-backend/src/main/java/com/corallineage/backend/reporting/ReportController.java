package com.corallineage.backend.reporting;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportGeneratorService reportGeneratorService;

    public ReportController(ReportGeneratorService reportGeneratorService) {
        this.reportGeneratorService = reportGeneratorService;
    }

    @PostMapping("/generate")
    public ReproducibilityReportResponse generate(@Valid @RequestBody GenerateReportRequest request) {
        return reportGeneratorService.generateReport(request);
    }

    @GetMapping
    public Page<ReportSummaryResponse> listReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return reportGeneratorService.listReports(pageable);
    }

    @GetMapping("/{reportId}/export")
    public ResponseEntity<ReproducibilityReportResponse> export(@PathVariable String reportId) {
        ReproducibilityReportResponse report = reportGeneratorService.exportReport(reportId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + reportId + ".json\"")
                .body(report);
    }
}
