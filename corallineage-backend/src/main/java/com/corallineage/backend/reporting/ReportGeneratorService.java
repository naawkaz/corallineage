package com.corallineage.backend.reporting;

import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEvent;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.AnnotationEventMapper;
import com.corallineage.backend.logging.AnnotationEventRepository;
import com.corallineage.backend.logging.AnnotationEventResponse;
import com.corallineage.backend.logging.EventType;
import com.corallineage.backend.ontology.DirtyAnnotationRepository;
import com.corallineage.backend.ontology.OntologyChange;
import com.corallineage.backend.ontology.OntologyChangeMapper;
import com.corallineage.backend.ontology.OntologyChangeRepository;
import com.corallineage.backend.ontology.OntologyChangeResponse;
import com.corallineage.backend.registry.model.MlflowDetailsResponse;
import com.corallineage.backend.registry.model.MlflowIntegrationService;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.registry.model.ModelVersionMapper;
import com.corallineage.backend.registry.model.ModelVersionRepository;
import com.corallineage.backend.registry.model.ModelVersionResponse;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionMapper;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersionResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportGeneratorService {

    private final ReportMetadataRepository reportMetadataRepository;
    private final DatasetVersionRepository datasetVersionRepository;
    private final DatasetVersionMapper datasetVersionMapper;
    private final ModelVersionRepository modelVersionRepository;
    private final ModelVersionMapper modelVersionMapper;
    private final OntologyChangeRepository ontologyChangeRepository;
    private final OntologyChangeMapper ontologyChangeMapper;
    private final DirtyAnnotationRepository dirtyAnnotationRepository;
    private final AnnotationEventRepository annotationEventRepository;
    private final AnnotationEventMapper annotationEventMapper;
    private final AnnotationEventLoggerService annotationEventLoggerService;
    private final AnnotationPointRepository annotationPointRepository;
    private final MlflowIntegrationService mlflowIntegrationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReportGeneratorService(ReportMetadataRepository reportMetadataRepository,
                                  DatasetVersionRepository datasetVersionRepository,
                                  DatasetVersionMapper datasetVersionMapper,
                                  ModelVersionRepository modelVersionRepository,
                                  ModelVersionMapper modelVersionMapper,
                                  OntologyChangeRepository ontologyChangeRepository,
                                  OntologyChangeMapper ontologyChangeMapper,
                                  DirtyAnnotationRepository dirtyAnnotationRepository,
                                  AnnotationEventRepository annotationEventRepository,
                                  AnnotationEventMapper annotationEventMapper,
                                  AnnotationEventLoggerService annotationEventLoggerService,
                                  AnnotationPointRepository annotationPointRepository,
                                  MlflowIntegrationService mlflowIntegrationService) {
        this.reportMetadataRepository = reportMetadataRepository;
        this.datasetVersionRepository = datasetVersionRepository;
        this.datasetVersionMapper = datasetVersionMapper;
        this.modelVersionRepository = modelVersionRepository;
        this.modelVersionMapper = modelVersionMapper;
        this.ontologyChangeRepository = ontologyChangeRepository;
        this.ontologyChangeMapper = ontologyChangeMapper;
        this.dirtyAnnotationRepository = dirtyAnnotationRepository;
        this.annotationEventRepository = annotationEventRepository;
        this.annotationEventMapper = annotationEventMapper;
        this.annotationEventLoggerService = annotationEventLoggerService;
        this.annotationPointRepository = annotationPointRepository;
        this.mlflowIntegrationService = mlflowIntegrationService;
    }

    @Transactional
    public ReproducibilityReportResponse generateReport(GenerateReportRequest request) {
        validateDateRange(request);

        String reportId = generateReportId();
        Instant generatedAt = Instant.now();
        ReproducibilityReportResponse response = buildReport(request, reportId, generatedAt);

        ReportMetadata metadata = new ReportMetadata();
        metadata.setReportId(reportId);
        metadata.setGeneratedAt(generatedAt);
        metadata.setRequestedBy(request.getRequestedBy());
        metadata.setFromDate(request.getFromDate());
        metadata.setToDate(request.getToDate());
        metadata.setIncludeEventLog(request.isIncludeEventLog());
        metadata.setIncludeOntologyChanges(request.isIncludeOntologyChanges());
        metadata.setIncludeModelRegistry(request.isIncludeModelRegistry());
        metadata.setIncludeDatasetVersionHistory(request.isIncludeDatasetVersionHistory());
        metadata.setIncludeAnalytics(request.isIncludeAnalytics());
        metadata.setIncludeMlflowDetails(request.isIncludeMlflowDetails());
        reportMetadataRepository.save(metadata);

        annotationEventLoggerService.log(EventType.REPORT_GENERATED, null, request.getRequestedBy(),
                "Reproducibility report " + reportId + " generated for "
                        + request.getFromDate() + " to " + request.getToDate());

        return response;
    }

    @Transactional(readOnly = true)
    public ReproducibilityReportResponse exportReport(String reportId) {
        ReportMetadata metadata = reportMetadataRepository.findByReportId(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found: " + reportId));

        GenerateReportRequest request = new GenerateReportRequest();
        request.setRequestedBy(metadata.getRequestedBy());
        request.setFromDate(metadata.getFromDate());
        request.setToDate(metadata.getToDate());
        request.setIncludeEventLog(metadata.isIncludeEventLog());
        request.setIncludeOntologyChanges(metadata.isIncludeOntologyChanges());
        request.setIncludeModelRegistry(metadata.isIncludeModelRegistry());
        request.setIncludeDatasetVersionHistory(metadata.isIncludeDatasetVersionHistory());
        request.setIncludeAnalytics(metadata.isIncludeAnalytics());
        request.setIncludeMlflowDetails(metadata.isIncludeMlflowDetails());

        return buildReport(request, metadata.getReportId(), metadata.getGeneratedAt());
    }

    private void validateDateRange(GenerateReportRequest request) {
        if (request.getFromDate().isAfter(request.getToDate())) {
            throw new InvalidRequestException("fromDate must not be after toDate");
        }
    }

    private String generateReportId() {
        int year = Instant.now().atZone(ZoneOffset.UTC).getYear();
        String prefix = "RPT-" + year + "-";
        long seq = reportMetadataRepository.countByReportIdStartingWith(prefix) + 1;
        return prefix + String.format("%03d", seq);
    }

    private ReproducibilityReportResponse buildReport(GenerateReportRequest request, String reportId, Instant generatedAt) {
        Instant from = request.getFromDate();
        Instant to = request.getToDate();

        List<DatasetVersion> versions = datasetVersionRepository.findByCreatedAtBetween(from, to);
        List<ModelVersion> models = modelVersionRepository.findByTrainedAtBetween(from, to);
        List<OntologyChange> changes = ontologyChangeRepository.findByChangedAtBetween(from, to);
        List<AnnotationEvent> events = annotationEventRepository
                .findByFilters(null, null, null, from, to, Pageable.unpaged())
                .getContent();

        ReportSummary summary = new ReportSummary(versions.size(), models.size(), changes.size(), events.size());

        List<DatasetVersionResponse> datasetVersionHistory = request.isIncludeDatasetVersionHistory()
                ? versions.stream().map(datasetVersionMapper::toResponse).toList()
                : List.of();
        List<ModelVersionResponse> modelRegistry = request.isIncludeModelRegistry()
                ? models.stream().map(modelVersionMapper::toResponse).toList()
                : List.of();
        List<OntologyChangeResponse> ontologyChangeResponses = request.isIncludeOntologyChanges()
                ? changes.stream()
                        .map(c -> ontologyChangeMapper.toResponse(c, dirtyAnnotationRepository.countByOntologyChangeId(c.getId())))
                        .toList()
                : List.of();
        List<AnnotationEventResponse> eventLog = request.isIncludeEventLog()
                ? events.stream().map(annotationEventMapper::toResponse).toList()
                : List.of();

        ReportAnalytics analytics = request.isIncludeAnalytics() ? buildAnalytics(request, models) : null;

        return new ReproducibilityReportResponse(reportId, generatedAt, request.getRequestedBy(),
                new ReportPeriod(from, to), summary, datasetVersionHistory, modelRegistry,
                ontologyChangeResponses, eventLog, analytics);
    }

    private ReportAnalytics buildAnalytics(GenerateReportRequest request, List<ModelVersion> modelsInScope) {
        List<AnnotationPointRepository.LabelCountRow> labelRows =
                annotationPointRepository.countByLabelBetween(request.getFromDate(), request.getToDate());
        long totalPoints = labelRows.stream().mapToLong(AnnotationPointRepository.LabelCountRow::getPointCount).sum();
        List<ClassDistributionItem> classDistribution = labelRows.stream()
                .map(r -> new ClassDistributionItem(r.getLabelCode(), r.getLabelName(), r.getPointCount(),
                        totalPoints > 0 ? (r.getPointCount() * 100.0 / totalPoints) : 0.0))
                .toList();

        List<AnnotationPointRepository.SourceCountRow> sourceRows =
                annotationPointRepository.countBySourceBetween(request.getFromDate(), request.getToDate());
        Map<String, Long> annotationSourceSplit = sourceRows.stream()
                .collect(Collectors.toMap(AnnotationPointRepository.SourceCountRow::getAnnotationSource,
                        AnnotationPointRepository.SourceCountRow::getPointCount, (a, b) -> a, LinkedHashMap::new));

        List<PerClassMetric> perClassMetrics = extractPerClassMetrics(modelsInScope);

        List<MlflowReportEntry> mlflowDetails = request.isIncludeMlflowDetails()
                ? buildMlflowEntries(modelsInScope)
                : List.of();

        return new ReportAnalytics(classDistribution, annotationSourceSplit, perClassMetrics, mlflowDetails);
    }

    /** Uses the most-recently-trained model in scope, since perClassMetrics has no per-model attribution in the spec's JSON shape. */
    private List<PerClassMetric> extractPerClassMetrics(List<ModelVersion> modelsInScope) {
        ModelVersion latest = modelsInScope.stream()
                .max(Comparator.comparing(ModelVersion::getTrainedAt))
                .orElse(null);
        if (latest == null || latest.getPerformanceMetrics() == null) {
            return List.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    latest.getPerformanceMetrics(), new TypeReference<Map<String, Object>>() {
                    });
            Object perClassObj = parsed.get("perClass");
            if (!(perClassObj instanceof List<?> rawList)) {
                return List.of();
            }
            List<PerClassMetric> result = new ArrayList<>();
            for (Object entry : rawList) {
                if (entry instanceof Map<?, ?> m) {
                    String labelCode = (String) m.get("labelCode");
                    Double accuracy = m.get("accuracy") instanceof Number n ? n.doubleValue() : null;
                    Long sampleCount = m.get("sampleCount") instanceof Number n ? n.longValue() : null;
                    result.add(new PerClassMetric(labelCode, accuracy, sampleCount));
                }
            }
            return result;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<MlflowReportEntry> buildMlflowEntries(List<ModelVersion> modelsInScope) {
        List<MlflowReportEntry> entries = new ArrayList<>();
        for (ModelVersion model : modelsInScope) {
            if (model.getMlflowRunId() == null) {
                continue;
            }
            MlflowDetailsResponse details = mlflowIntegrationService.buildMlflowDetails(model);
            if (details == null) {
                continue;
            }
            String confusionMatrixUrl = details.getArtifactUrls() != null
                    ? details.getArtifactUrls().confusionMatrixPng()
                    : null;
            entries.add(new MlflowReportEntry(model.getModelTag(), model.getMlflowRunId(),
                    details.getEnvironment(), details.getEpochMetrics(), confusionMatrixUrl));
        }
        return entries;
    }
}
