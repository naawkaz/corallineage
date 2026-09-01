package com.corallineage.backend.training;

import com.corallineage.backend.datasets.Dataset;
import com.corallineage.backend.datasets.DatasetRepository;
import com.corallineage.backend.versioning.dataset.DatasetVersion;
import com.corallineage.backend.versioning.dataset.DatasetVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Real-transaction test that a training job on a version with no points fails cleanly, needs the dev Postgres database */
@SpringBootTest
class TrainingJobExecutorIntegrationTest {

    @Autowired
    private TrainingJobExecutor trainingJobExecutor;

    @Autowired
    private TrainingJobRepository trainingJobRepository;

    @Autowired
    private DatasetVersionRepository datasetVersionRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @Test
    void executeTrainingAsync_datasetVersionWithNoAnnotationPoints_marksJobFailedInsteadOfStuckAtQueued() throws Exception {
        Dataset dataset = new Dataset();
        dataset.setDatasetName("Integration Test Dataset " + System.nanoTime());
        dataset.setCreatedBy("integration-test");
        dataset = datasetRepository.save(dataset);

        DatasetVersion version = new DatasetVersion();
        version.setDataset(dataset);
        version.setVersionTag("v-test");
        version.setCreatedBy("integration-test");
        version.setTriggerReason("MANUAL_SNAPSHOT");
        version.setImageCount(0);
        version.setAnnotationCount(0);
        version.setDvcCommitHash("deadbeef");
        version = datasetVersionRepository.save(version);

        TrainingJob job = new TrainingJob();
        job.setDatasetVersion(version);
        job.setArchitecture("efficientnet_b0");
        job.setInitiatedBy("integration-test");
        job.setStatus("QUEUED");
        job.setHyperparameters("{\"learningRate\":0.001,\"batchSize\":32,\"epochs\":10}");
        job = trainingJobRepository.save(job);
        Long jobId = job.getId();

        try {
            trainingJobExecutor.executeTrainingAsync(jobId);

            TrainingJob result = awaitTerminalStatus(jobId);

            assertThat(result.getStatus())
                    .as("job must reach a terminal FAILED state, not stay stuck at QUEUED or RUNNING")
                    .isEqualTo("FAILED");
            assertThat(result.getErrorMessage()).contains("no annotation points");
            assertThat(result.getCompletedAt()).isNotNull();
        } finally {
            trainingJobRepository.deleteById(jobId);
            datasetVersionRepository.deleteById(version.getId());
            datasetRepository.deleteById(dataset.getId());
        }
    }

    private TrainingJob awaitTerminalStatus(Long jobId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        TrainingJob job;
        do {
            job = trainingJobRepository.findById(jobId).orElseThrow();
            if (!"QUEUED".equals(job.getStatus()) && !"RUNNING".equals(job.getStatus())) {
                return job;
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);
        return job;
    }
}
