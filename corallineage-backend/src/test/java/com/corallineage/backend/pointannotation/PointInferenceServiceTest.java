package com.corallineage.backend.pointannotation;

import com.corallineage.backend.exception.NotFoundException;
import com.corallineage.backend.registry.model.ModelVersion;
import com.corallineage.backend.training.AnnotateImageInput;
import com.corallineage.backend.training.AnnotateResult;
import com.corallineage.backend.training.PythonMlServiceClient;
import com.corallineage.backend.versioning.dataset.AnnotationPoint;
import com.corallineage.backend.versioning.dataset.AnnotationPointRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.Image;
import com.corallineage.backend.versioning.dataset.LabelDefinition;
import com.corallineage.backend.versioning.dataset.LabelDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * prepareAndStartAnnotate and applyPredictions are now two separate @Transactional entry points
 * (see PointInferenceService's class comment for why: AutoAnnotationExecutor's poll loop between
 * them can run for seconds to minutes and must not hold one transaction open across that span),
 * so this file covers each in isolation rather than one combined runInference call. Coverage of
 * the two wired together end-to-end lives in AutoAnnotationExecutorTest.
 */
@ExtendWith(MockitoExtension.class)
class PointInferenceServiceTest {

    @Mock private PointAnnotationJobRepository pointAnnotationJobRepository;
    @Mock private GeneratedPointRepository generatedPointRepository;
    @Mock private LabelDefinitionRepository labelDefinitionRepository;
    @Mock private AnnotationPointRepository annotationPointRepository;
    @Mock private PythonMlServiceClient pythonMlServiceClient;

    private PointInferenceService newService(Path workingDirectory) {
        return new PointInferenceService(pointAnnotationJobRepository, generatedPointRepository, labelDefinitionRepository,
                new AnnotationPointBridge(annotationPointRepository), pythonMlServiceClient, workingDirectory, 64);
    }

    private void writeTestImage(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "jpg", file.toFile());
    }

    private GeneratedPoint point(Long id, Image image, int pointIndex, PointAnnotationJob job) {
        GeneratedPoint point = new GeneratedPoint();
        point.setId(id);
        point.setImage(image);
        point.setPointIndex(pointIndex);
        point.setRowPosition(50);
        point.setColPosition(50);
        point.setJob(job);
        point.setSeed(false);
        point.setStatus("UNLABELED");
        return point;
    }

    @Test
    void prepareAndStartAnnotate_jobNotFound_throwsNotFound(@TempDir Path workingDirectory) {
        when(pointAnnotationJobRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService(workingDirectory).prepareAndStartAnnotate(1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void prepareAndStartAnnotate_noPendingPoints_reportsNoPendingWorkAndSkipsPythonCall(@TempDir Path workingDirectory) {
        PointAnnotationJob job = new PointAnnotationJob();
        job.setId(1L);
        when(pointAnnotationJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(generatedPointRepository.findByJobIdAndSeedFalseAndStatus(1L, "UNLABELED")).thenReturn(List.of());

        PointInferenceService.AnnotationKickoff kickoff = newService(workingDirectory).prepareAndStartAnnotate(1L);

        assertThat(kickoff.hasPendingWork()).isFalse();
        verify(pythonMlServiceClient, never()).startAnnotate(any(), any(), any(), any(), any());
    }

    @Test
    void prepareAndStartAnnotate_pendingPoints_cropsPatchesAndStartsBatch(@TempDir Path workingDirectory) throws IOException {
        writeTestImage(workingDirectory.resolve("dataset/img001.jpg"));

        DatasetVersion version = new DatasetVersion();
        version.setId(10L);

        PointAnnotationJob job = new PointAnnotationJob();
        job.setId(1L);
        job.setDatasetVersion(version);
        job.setConfidenceThreshold(new BigDecimal("0.80"));
        ModelVersion model = new ModelVersion();
        model.setId(9L);
        model.setModelTag("model-v9");
        model.setArchitecture("efficientnet_b0");
        model.setWeightsPath("/weights/model-v9.pt");
        model.setClassMappings("{\"0\":\"CCA\",\"1\":\"SAND\"}");
        job.setModelVersion(model);

        Image image = new Image();
        image.setId(5L);
        image.setFilename("img001.jpg");
        image.setFilePath("dataset/img001.jpg");

        GeneratedPoint pending = point(100L, image, 200, job);
        when(pointAnnotationJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(generatedPointRepository.findByJobIdAndSeedFalseAndStatus(1L, "UNLABELED")).thenReturn(List.of(pending));

        PointInferenceService.AnnotationKickoff kickoff = newService(workingDirectory).prepareAndStartAnnotate(1L);

        assertThat(kickoff.hasPendingWork()).isTrue();
        assertThat(kickoff.patchDir()).exists();
        assertThat(Files.list(kickoff.patchDir())).hasSize(1);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<AnnotateImageInput>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(pythonMlServiceClient).startAnnotate(eq("efficientnet_b0"), eq("/weights/model-v9.pt"), any(), captor.capture(), eq(1L));
        assertThat(captor.getValue()).extracting(AnnotateImageInput::imageId).containsExactly(100L);
    }

    @Test
    void applyPredictions_appliesConfidenceThreshold_aboveConfirmsBelowStaysPending(@TempDir Path workingDirectory) {
        DatasetVersion version = new DatasetVersion();
        version.setId(10L);

        PointAnnotationJob job = new PointAnnotationJob();
        job.setId(1L);
        job.setDatasetVersion(version);
        job.setConfidenceThreshold(new BigDecimal("0.80"));
        ModelVersion model = new ModelVersion();
        model.setId(9L);
        model.setModelTag("model-v9");
        job.setModelVersion(model);

        Image image = new Image();
        image.setId(5L);
        image.setFilename("img001.jpg");
        image.setFilePath("dataset/img001.jpg");

        GeneratedPoint highConfidencePoint = point(100L, image, 200, job);
        GeneratedPoint lowConfidencePoint = point(101L, image, 201, job);
        when(pointAnnotationJobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(generatedPointRepository.findAllById(any()))
                .thenReturn(List.of(highConfidencePoint, lowConfidencePoint));

        LabelDefinition cca = new LabelDefinition();
        cca.setLabelCode("CCA");
        LabelDefinition sand = new LabelDefinition();
        sand.setLabelCode("SAND");
        when(labelDefinitionRepository.findByDatasetVersionIdAndLabelCode(10L, "CCA")).thenReturn(Optional.of(cca));
        when(labelDefinitionRepository.findByDatasetVersionIdAndLabelCode(10L, "SAND")).thenReturn(Optional.of(sand));

        AnnotateResult result = new AnnotateResult(List.of(
                new AnnotateResult.AnnotatedImage(100L, List.of(
                        new AnnotateResult.AnnotatedPoint(0, "CCA", new BigDecimal("0.92")))),
                new AnnotateResult.AnnotatedImage(101L, List.of(
                        new AnnotateResult.AnnotatedPoint(0, "SAND", new BigDecimal("0.40"))))
        ));
        when(annotationPointRepository.findByImageIdAndPointIndex(any(), any())).thenReturn(Optional.empty());
        when(annotationPointRepository.save(any(AnnotationPoint.class))).thenAnswer(inv -> inv.getArgument(0));

        newService(workingDirectory).applyPredictions(1L, result);

        assertThat(highConfidencePoint.getStatus()).isEqualTo("CONFIRMED");
        assertThat(highConfidencePoint.getFinalLabelDefinition()).isEqualTo(cca);
        assertThat(lowConfidencePoint.getStatus()).isEqualTo("MODEL_PREDICTED");
        assertThat(lowConfidencePoint.getFinalLabelDefinition()).isNull();
        assertThat(lowConfidencePoint.getPredictedLabelDefinition()).isEqualTo(sand);

        // Only the above-threshold point bridges into AnnotationPoint.
        verify(annotationPointRepository, times(1)).save(any(AnnotationPoint.class));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<GeneratedPoint>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(generatedPointRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(highConfidencePoint, lowConfidencePoint);
    }
}
