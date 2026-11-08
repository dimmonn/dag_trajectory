package com.uj.graph.dag_trajectory.service;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SzzAnalysisServiceTest {

    @Test
    void acceptsOnlySourceCommitsNotLaterThanTheBugReport() {
        long reportTime = Instant.parse("2020-03-10T12:00:00Z").getEpochSecond();

        assertTrue(SzzAnalysisService.isEligibleCandidate(
                Instant.parse("2020-03-10T11:59:59Z").getEpochSecond(), reportTime));
        assertTrue(SzzAnalysisService.isEligibleCandidate(reportTime, reportTime));
        assertFalse(SzzAnalysisService.isEligibleCandidate(
                Instant.parse("2020-03-10T12:00:01Z").getEpochSecond(), reportTime));
        assertFalse(SzzAnalysisService.isEligibleCandidate(reportTime, null));
    }
}
