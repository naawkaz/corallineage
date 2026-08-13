package com.corallineage.backend.reporting;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.exception.InvalidRequestException;
import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.logging.AnnotationEvent;
import com.corallineage.backend.logging.AnnotationEventLoggerService;
import com.corallineage.backend.logging.AnnotationEventMapper;
import com.corallineage.backend.logging.AnnotationEventRepository;
import com.corallineage.backend.ontology.DirtyAnnotationRepository;
import com.corallineage.backend.ontology.OntologyChange;
import com.corallineage.backend.ontology.OntologyChangeMapper;
import com.corallineage.backend.ontology.OntologyChangeRepository;
import com.corallineage.backend.registry.model.MlflowIntegrationService;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.registry.model.ModelVersionMapper;
import com.corallineage.backend.registry.model.ModelVersionRepository;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionMapper;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGeneratorServiceTest {

    @Mock private ReportMetadataRepository reportMetadataRepository;
    @Mock private DatasetVersionRepository datasetVersionRepository;
    @Mock private ModelVersionRepository modelVersionRepository;
    @Mock private OntologyChangeRepository ontologyChangeRepository;
    @Mock private DirtyAnnotationRepository dirtyAnnotationRepository;
    @Mock private AnnotationEventRepository annotationEventRepository;
    @Mock private AnnotationEventLoggerService annotationEventLoggerService;
    @Mock private AnnotationPointRepository annotationPointRepository;
    @Mock private MlflowIntegrationService mlflowIntegrationService;

    private final DatasetVersionMapper datasetVersionMapper = new DatasetVersionMapper();
    private final ModelVersionMapper modelVersionMapper = new ModelVersionMapper();
    private final OntologyChangeMapper ontologyChangeMapper = new OntologyChangeMapper();
    private final AnnotationEventMapper annotationEventMapper = new AnnotationEventMapper();

    private final Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant t2 = Instant.parse("2026-06-01T00:00:00Z");

    private ReportGeneratorService newService() {
        return new ReportGeneratorService(reportMetadataRepository, datasetVersionRepository, datasetVersionMapper,
                modelVersionRepository, modelVersionMapper, ontologyChangeRepository, ontologyChangeMapper,
                dirtyAnnotationRepository, annotationEventRepository, annotationEventMapper,
                annotationEventLoggerService, annotationPointRepository, mlflowIntegrationService);
    }

    private GenerateReportRequest request(boolean includeEverything) {
        GenerateReportRequest req = new GenerateReportRequest();
        req.setRequestedBy("researcher_01");
        req.setFromDate(t1);
        req.setToDate(t2);
        req.setIncludeEventLog(includeEverything);
        req.setIncludeOntologyChanges(includeEverything);
        req.setIncludeModelRegistry(includeEverything);
        req.setIncludeDatasetVersionHistory(includeEverything);
        req.setIncludeAnalytics(includeEverything);
        req.setIncludeMlflowDetails(includeEverything);
        return req;
    }

    private DatasetVersion version(Long id, String tag) {
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        DatasetVersion v = new DatasetVersion();
        v.setId(id);
        v.setDataset(dataset);
        v.setVersionTag(tag);
        v.setCreatedAt(t1);
        return v;
    }

    private ModelVersion model(Long id, String tag, String mlflowRunId) {
        ModelVersion m = new ModelVersion();
        m.setId(id);
        m.setModelTag(tag);
        m.setTrainedAt(t1);
        m.setArchitecture("efficientnet_b0");
        m.setClassMappings("{\"0\":\"A\"}");
        m.setStatus("ACTIVE");
        m.setMlflowRunId(mlflowRunId);
        m.setPerformanceMetrics("{\"perClass\":[{\"labelCode\":\"A\",\"accuracy\":0.9,\"sampleCount\":10}]}");
        return m;
    }

    private OntologyChange ontologyChange(Long id) {
        OntologyChange c = new OntologyChange();
        c.setId(id);
        c.setChangeType("LABEL_RENAMED");
        c.setOldLabelCode("OLD");
        c.setChangedBy("researcher_01");
        c.setChangedAt(t1);
        return c;
    }

    private AnnotationEvent event(Long id) {
        AnnotationEvent e = new AnnotationEvent();
        e.setId(id);
        e.setEventType(com.corallineage.backend.logging.EventType.MODEL_TRAINED);
        e.setPerformedBy("researcher_01");
        e.setOccurredAt(t1);
        return e;
    }

    private AnnotationPointRepository.LabelCountRow labelRow(String code, String name, long count) {
        AnnotationPointRepository.LabelCountRow row = mock(AnnotationPointRepository.LabelCountRow.class);
        when(row.getLabelCode()).thenReturn(code);
        when(row.getLabelName()).thenReturn(name);
        when(row.getPointCount()).thenReturn(count);
        return row;
    }

    private AnnotationPointRepository.SourceCountRow sourceRow(String source, long count) {
        AnnotationPointRepository.SourceCountRow row = mock(AnnotationPointRepository.SourceCountRow.class);
        when(row.getAnnotationSource()).thenReturn(source);
        when(row.getPointCount()).thenReturn(count);
        return row;
    }

    @Test
    void generateReport_fromDateAfterToDate_throwsInvalidRequest() {
        GenerateReportRequest req = request(true);
        req.setFromDate(t2);
        req.setToDate(t1);

        assertThatThrownBy(() -> newService().generateReport(req)).isInstanceOf(InvalidRequestException.class);
        verify(reportMetadataRepository, never()).save(any());
    }

    @Test
    void generateReport_analyticsComputesClassDistributionAndSourceSplit() {
        GenerateReportRequest req = request(true);
        stubEmptyCoreQueries();
        AnnotationPointRepository.LabelCountRow rowA = labelRow("A", "Label A", 3);
        AnnotationPointRepository.LabelCountRow rowB = labelRow("B", "Label B", 1);
        when(annotationPointRepository.countByLabelBetween(t1, t2)).thenReturn(List.of(rowA, rowB));
        AnnotationPointRepository.SourceCountRow sourceHuman = sourceRow("HUMAN", 2);
        AnnotationPointRepository.SourceCountRow sourceModel = sourceRow("MODEL_PREDICTED", 2);
        when(annotationPointRepository.countBySourceBetween(t1, t2)).thenReturn(List.of(sourceHuman, sourceModel));
        when(reportMetadataRepository.countByReportIdStartingWith(any())).thenReturn(0L);
        when(reportMetadataRepository.save(any(ReportMetadata.class))).thenAnswer(inv -> inv.getArgument(0));

        ReproducibilityReportResponse response = newService().generateReport(req);

        assertThat(response.analytics()).isNotNull();
        assertThat(response.analytics().classDistribution()).hasSize(2);
        var entryA = response.analytics().classDistribution().stream()
                .filter(c -> c.labelCode().equals("A")).findFirst().orElseThrow();
        assertThat(entryA.count()).isEqualTo(3);
        assertThat(entryA.percentage()).isEqualTo(75.0);
        assertThat(response.analytics().annotationSourceSplit()).containsEntry("HUMAN", 2L)
                .containsEntry("MODEL_PREDICTED", 2L);
        assertThat(response.reportId()).startsWith("RPT-");
        verify(reportMetadataRepository).save(any(ReportMetadata.class));
        verify(annotationEventLoggerService).log(eq(com.corallineage.backend.logging.EventType.REPORT_GENERATED),
                any(), eq("researcher_01"), any());
    }

    @Test
    void generateReport_mlflowUnreachable_skipsModelGracefully() {
        GenerateReportRequest req = request(true);
        ModelVersion m = model(1L, "model-v1", "run-123");
        when(datasetVersionRepository.findByCreatedAtBetween(t1, t2)).thenReturn(List.of());
        when(modelVersionRepository.findByTrainedAtBetween(t1, t2)).thenReturn(List.of(m));
        when(ontologyChangeRepository.findByChangedAtBetween(t1, t2)).thenReturn(List.of());
        when(annotationEventRepository.findByFilters(any(), any(), any(), any(), eq(t1), eq(t2), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(annotationPointRepository.countByLabelBetween(t1, t2)).thenReturn(List.of());
        when(annotationPointRepository.countBySourceBetween(t1, t2)).thenReturn(List.of());
        when(mlflowIntegrationService.buildMlflowDetails(m)).thenReturn(null);
        when(reportMetadataRepository.countByReportIdStartingWith(any())).thenReturn(0L);
        when(reportMetadataRepository.save(any(ReportMetadata.class))).thenAnswer(inv -> inv.getArgument(0));

        ReproducibilityReportResponse response = newService().generateReport(req);

        assertThat(response.analytics().mlflowDetails()).isEmpty();
        assertThat(response.summary().modelsRegistered()).isEqualTo(1);
    }

    @Test
    void generateReport_togglesOff_listsEmptyButSummaryPopulated() {
        GenerateReportRequest req = request(false);
        when(datasetVersionRepository.findByCreatedAtBetween(t1, t2)).thenReturn(List.of(version(1L, "v1.0")));
        when(modelVersionRepository.findByTrainedAtBetween(t1, t2)).thenReturn(List.of(model(1L, "model-v1", null)));
        when(ontologyChangeRepository.findByChangedAtBetween(t1, t2)).thenReturn(List.of(ontologyChange(1L)));
        when(annotationEventRepository.findByFilters(any(), any(), any(), any(), eq(t1), eq(t2), any()))
                .thenReturn(new PageImpl<>(List.of(event(1L))));
        when(reportMetadataRepository.countByReportIdStartingWith(any())).thenReturn(0L);
        when(reportMetadataRepository.save(any(ReportMetadata.class))).thenAnswer(inv -> inv.getArgument(0));

        ReproducibilityReportResponse response = newService().generateReport(req);

        assertThat(response.datasetVersionHistory()).isEmpty();
        assertThat(response.modelRegistry()).isEmpty();
        assertThat(response.ontologyChanges()).isEmpty();
        assertThat(response.eventLog()).isEmpty();
        assertThat(response.analytics()).isNull();

        assertThat(response.summary().datasetVersionsCreated()).isEqualTo(1);
        assertThat(response.summary().modelsRegistered()).isEqualTo(1);
        assertThat(response.summary().ontologyChanges()).isEqualTo(1);
        assertThat(response.summary().totalAnnotationEvents()).isEqualTo(1);
    }

    @Test
    void exportReport_unknownReportId_throwsNotFound() {
        when(reportMetadataRepository.findByReportId("RPT-9999-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().exportReport("RPT-9999-999")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void exportReport_reproducesSameNumbersWithoutCreatingNewMetadata() {
        ReportMetadata metadata = new ReportMetadata();
        metadata.setReportId("RPT-2026-001");
        metadata.setGeneratedAt(t1);
        metadata.setRequestedBy("researcher_01");
        metadata.setFromDate(t1);
        metadata.setToDate(t2);
        metadata.setIncludeDatasetVersionHistory(true);
        when(reportMetadataRepository.findByReportId("RPT-2026-001")).thenReturn(Optional.of(metadata));
        when(datasetVersionRepository.findByCreatedAtBetween(t1, t2)).thenReturn(List.of(version(1L, "v1.0")));
        when(modelVersionRepository.findByTrainedAtBetween(t1, t2)).thenReturn(List.of());
        when(ontologyChangeRepository.findByChangedAtBetween(t1, t2)).thenReturn(List.of());
        when(annotationEventRepository.findByFilters(any(), any(), any(), any(), eq(t1), eq(t2), any()))
                .thenReturn(new PageImpl<>(List.of()));

        ReproducibilityReportResponse response = newService().exportReport("RPT-2026-001");

        assertThat(response.reportId()).isEqualTo("RPT-2026-001");
        assertThat(response.datasetVersionHistory()).hasSize(1);
        verify(reportMetadataRepository, never()).save(any());
        verify(annotationEventLoggerService, never()).log(any(), any(), any(), any());
    }

    @Test
    void listReports_returnsMappedSummariesInOrder() {
        ReportMetadata m1 = new ReportMetadata();
        m1.setReportId("RPT-2026-002");
        m1.setGeneratedAt(t2);
        m1.setRequestedBy("researcher_02");
        m1.setFromDate(t1);
        m1.setToDate(t2);

        ReportMetadata m2 = new ReportMetadata();
        m2.setReportId("RPT-2026-001");
        m2.setGeneratedAt(t1);
        m2.setRequestedBy("researcher_01");
        m2.setFromDate(t1);
        m2.setToDate(t2);

        Pageable pageable = PageRequest.of(0, 20);
        when(reportMetadataRepository.findAllByOrderByGeneratedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(m1, m2)));

        Page<ReportSummaryResponse> result = newService().listReports(pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).reportId()).isEqualTo("RPT-2026-002");
        assertThat(result.getContent().get(0).requestedBy()).isEqualTo("researcher_02");
        assertThat(result.getContent().get(0).fromDate()).isEqualTo(t1);
        assertThat(result.getContent().get(0).toDate()).isEqualTo(t2);
        assertThat(result.getContent().get(0).generatedAt()).isEqualTo(t2);
        assertThat(result.getContent().get(1).reportId()).isEqualTo("RPT-2026-001");
        assertThat(result.getContent().get(1).requestedBy()).isEqualTo("researcher_01");
    }

    private void stubEmptyCoreQueries() {
        when(datasetVersionRepository.findByCreatedAtBetween(t1, t2)).thenReturn(List.of());
        when(modelVersionRepository.findByTrainedAtBetween(t1, t2)).thenReturn(List.of());
        when(ontologyChangeRepository.findByChangedAtBetween(t1, t2)).thenReturn(List.of());
        Page<AnnotationEvent> emptyPage = new PageImpl<>(List.of());
        when(annotationEventRepository.findByFilters(any(), any(), any(), any(), eq(t1), eq(t2), any())).thenReturn(emptyPage);
    }
}
