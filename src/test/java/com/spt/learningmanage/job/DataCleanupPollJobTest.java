package com.spt.learningmanage.job;

import com.spt.learningmanage.config.DataCleanupProperties;
import com.spt.learningmanage.model.entity.AiDataCleanupRun;
import com.spt.learningmanage.service.CleanupRunQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataCleanupPollJobTest {
    @Mock CleanupRunQueueService queueService;
    @Mock DataCleanupWorker worker;
    @Mock Executor executor;

    private DataCleanupProperties properties;
    private DataCleanupPollJob job;

    @BeforeEach
    void setUp() {
        properties = new DataCleanupProperties();
        properties.setEnabled(true);
        job = new DataCleanupPollJob(properties, queueService, worker, executor);
    }

    @Test
    void dispatchesClaimedRunWithoutBlockingTheSchedulerThread() {
        AiDataCleanupRun run = new AiDataCleanupRun();
        when(queueService.claimOne(any())).thenReturn(run);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);

        job.poll();

        verify(executor).execute(task.capture());
        verifyNoInteractions(worker);
        task.getValue().run();
        verify(worker).process(run);
    }

    @Test
    void releasesLeaseWhenDedicatedExecutorRejectsDispatch() {
        AiDataCleanupRun run = new AiDataCleanupRun();
        when(queueService.claimOne(any())).thenReturn(run);
        doThrow(new RejectedExecutionException("shutdown")).when(executor).execute(any());

        job.poll();

        verify(queueService).releaseForResume(run);
        verifyNoInteractions(worker);
    }
}
