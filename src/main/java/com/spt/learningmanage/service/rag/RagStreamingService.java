package com.spt.learningmanage.service.rag;

import com.spt.learningmanage.config.RagProperties;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.model.dto.rag.RagAskRequest;
import com.spt.learningmanage.model.vo.rag.RagAnswerVO;
import com.spt.learningmanage.observability.AiMetricsRecorder;
import com.spt.learningmanage.service.RagService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RagStreamingService {

    private final RagService ragService;
    private final RagProperties properties;
    private final ExecutorService executor;
    private AiMetricsRecorder metricsRecorder;

    public RagStreamingService(RagService ragService,
                               RagProperties properties,
                               ExecutorService ragStreamingExecutor) {
        this.ragService = ragService;
        this.properties = properties;
        this.executor = ragStreamingExecutor;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setMetricsRecorder(AiMetricsRecorder metricsRecorder) {
        this.metricsRecorder = metricsRecorder;
    }

    public SseEmitter start(RagAskRequest request, Long actorUserId) {
        SseEmitter emitter = new SseEmitter((long) properties.getStreamTimeoutMs());
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean terminal = new AtomicBoolean(false);
        AtomicReference<Future<?>> task = new AtomicReference<>();
        emitter.onTimeout(() -> {
            cancelled.set(true);
            cancel(task);
            if (terminal.compareAndSet(false, true)) {
                emitter.complete();
            }
        });
        emitter.onError(error -> {
            if (!terminal.get()) {
                cancelled.set(true);
                cancel(task);
            }
        });
        emitter.onCompletion(() -> {
            if (!terminal.get()) {
                cancelled.set(true);
                cancel(task);
            }
        });

        Runnable work = () -> run(request, actorUserId, emitter, cancelled, terminal);
        try {
            task.set(executor.submit(work));
        } catch (RejectedExecutionException exception) {
            emitter.completeWithError(new BusinessException(ErrorCode.AI_CONCURRENCY_LIMIT));
        }
        return emitter;
    }

    private void run(RagAskRequest request,
                     Long actorUserId,
                     SseEmitter emitter,
                     AtomicBoolean cancelled,
                     AtomicBoolean terminal) {
        String requestId = null;
        long startedAt = System.currentTimeMillis();
        try {
            requestId = requestId(request);
            send(emitter, "accepted", Map.of("requestId", requestId), cancelled);
            String acceptedRequestId = requestId;
            RagExecutionObserver observer = new RagExecutionObserver() {
                @Override
                public void onStage(String stage, int attempt) {
                    send(emitter, "stage", Map.of(
                            "requestId", acceptedRequestId,
                            "stage", stage,
                            "attempt", attempt), cancelled);
                }

                @Override
                public boolean isCancelled() {
                    return cancelled.get();
                }
            };
            RagAnswerVO answer = ragService.ask(request, actorUserId, observer, requestId);
            observer.checkCancelled();
            send(emitter, "complete", answer, cancelled);
            record("COMPLETED", startedAt);
            terminal.set(true);
            emitter.complete();
        } catch (RagStreamCancelledException exception) {
            record("CANCELED", startedAt);
            terminal.set(true);
            emitter.complete();
        } catch (BusinessException exception) {
            if (cancelled.get()) {
                record("CANCELED", startedAt);
                terminal.set(true);
                emitter.complete();
                return;
            }
            record("FAILED", startedAt);
            sendError(emitter, requestId, exception.getErrorCode(), cancelled);
            terminal.set(true);
            emitter.complete();
        } catch (Exception exception) {
            if (cancelled.get()) {
                record("CANCELED", startedAt);
                terminal.set(true);
                emitter.complete();
                return;
            }
            record("FAILED", startedAt);
            sendError(emitter, requestId, ErrorCode.SYSTEM_ERROR, cancelled);
            terminal.set(true);
            emitter.complete();
        }
    }

    private void record(String outcome, long startedAt) {
        if (metricsRecorder != null) {
            metricsRecorder.recordRagStream(outcome, System.currentTimeMillis() - startedAt);
        }
    }

    private void sendError(SseEmitter emitter,
                           String requestId,
                           ErrorCode errorCode,
                           AtomicBoolean cancelled) {
        if (cancelled.get()) {
            return;
        }
        Map<String, Object> data = requestId == null
                ? Map.of("code", errorCode.getCode(), "message", errorCode.getMessage())
                : Map.of("requestId", requestId, "code", errorCode.getCode(),
                "message", errorCode.getMessage());
        send(emitter, "error", data, cancelled);
    }

    private void send(SseEmitter emitter,
                      String event,
                      Object data,
                      AtomicBoolean cancelled) {
        if (cancelled.get()) {
            throw new RagStreamCancelledException();
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException exception) {
            cancelled.set(true);
            throw new RagStreamCancelledException();
        }
    }

    private String requestId(RagAskRequest request) {
        // The same opaque id is passed into the persisted RAG execution so the
        // accepted, complete and error events can be correlated exactly.
        return java.util.UUID.randomUUID().toString();
    }

    private void cancel(AtomicReference<Future<?>> task) {
        Future<?> future = task.get();
        if (future != null) {
            future.cancel(true);
        }
    }
}
