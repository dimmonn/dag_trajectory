package com.uj.graph.dag_trajectory.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BatchProgressService {

    private static final Logger logger = LoggerFactory.getLogger(BatchProgressService.class);
    private static final long REPORT_INTERVAL_MILLIS = 10_000;

    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final AtomicLong lastReportTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong startTime = new AtomicLong(0);
    private String batchId;
    private String batchType;

    public void startBatch(String batchId, String batchType, int totalItems) {
        this.batchId = batchId;
        this.batchType = batchType;
        this.totalCount.set(totalItems);
        this.processedCount.set(0);
        this.startTime.set(System.currentTimeMillis());
        this.lastReportTime.set(System.currentTimeMillis());
        
        logger.info("[{}] Starting batch: {} (type: {}, total items: {})",
                batchId, batchType, batchType, totalItems);
    }

    public void recordProgress(int itemsProcessed) {
        recordProgress(itemsProcessed, null);
    }

    public void recordProgress(int itemsProcessed, String detail) {
        int newCount = processedCount.addAndGet(itemsProcessed);
        long now = System.currentTimeMillis();
        long timeSinceLastReport = now - lastReportTime.get();
        
        if (timeSinceLastReport >= REPORT_INTERVAL_MILLIS || newCount >= totalCount.get()) {
            long elapsedSeconds = (now - startTime.get()) / 1000;
            double progressPercent = (newCount * 100.0) / totalCount.get();
            double itemsPerSecond = newCount > 0 ? newCount / (elapsedSeconds > 0 ? elapsedSeconds : 1.0) : 0;
            long estimatedRemainingSeconds = 0;
            if (itemsPerSecond > 0 && newCount < totalCount.get()) {
                estimatedRemainingSeconds = (long) ((totalCount.get() - newCount) / itemsPerSecond);
            }
            
            String message = String.format(
                "[%s] Progress: %d/%d (%.1f%%) | Elapsed: %ds | Rate: %.1f items/sec | ETA: %ds",
                batchId, newCount, totalCount.get(), progressPercent, elapsedSeconds, itemsPerSecond, estimatedRemainingSeconds
            );
            
            if (detail != null && !detail.isBlank()) {
                message += " | " + detail;
            }
            
            logger.info(message);
            lastReportTime.set(now);
        }
    }

    public void completeBatch() {
        long elapsedSeconds = (System.currentTimeMillis() - startTime.get()) / 1000;
        int processed = processedCount.get();
        double itemsPerSecond = processed > 0 ? processed / (double) (elapsedSeconds > 0 ? elapsedSeconds : 1) : 0;
        
        logger.info("[{}] Batch completed: {} | Total time: {}s | Processed: {} items | Rate: {:.1f} items/sec",
                batchId, batchType, elapsedSeconds, processed, itemsPerSecond);
        
        reset();
    }

    public void cancelBatch(String reason) {
        long elapsedSeconds = (System.currentTimeMillis() - startTime.get()) / 1000;
        logger.warn("[{}] Batch cancelled: {} | Reason: {} | Processed: {}/{} | Elapsed: {}s",
                batchId, batchType, reason, processedCount.get(), totalCount.get(), elapsedSeconds);
        
        reset();
    }

    public int getProcessedCount() {
        return processedCount.get();
    }

    public int getTotalCount() {
        return totalCount.get();
    }

    public double getProgressPercent() {
        return totalCount.get() > 0 ? (processedCount.get() * 100.0) / totalCount.get() : 0;
    }

    private void reset() {
        processedCount.set(0);
        totalCount.set(0);
        startTime.set(0);
        lastReportTime.set(System.currentTimeMillis());
    }
}
