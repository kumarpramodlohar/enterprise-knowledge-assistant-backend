package org.enterpriseknowledgeassitance.chat.crag;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class CragModel {
    private final ChatClient chat;
    private final ExecutorService calls = Executors.newVirtualThreadPerTaskExecutor();

    public CragModel(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    public String complete(String stage, String system, String prompt, long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new CragStageException(stage);
        }
        Future<String> request = calls.submit(() -> chat.prompt().system(system).user(prompt).call().content());
        try {
            String result = request.get(remainingNanos, TimeUnit.NANOSECONDS);
            if (result == null || result.isBlank()) {
                throw new CragStageException(stage);
            }
            return result;
        } catch (TimeoutException e) {
            request.cancel(true);
            throw new CragStageException(stage, e);
        } catch (ExecutionException e) {
            throw new CragStageException(stage, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CragStageException(stage, e);
        }
    }

    @PreDestroy
    void shutdown() {
        calls.shutdownNow();
    }
}
