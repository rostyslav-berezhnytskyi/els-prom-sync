package com.els.promsync.service;

import com.els.promsync.dto.SyncChangeType;
import com.els.promsync.dto.SyncItemChange;
import com.els.promsync.dto.SyncReport;
import com.els.promsync.entity.SyncRun;
import com.els.promsync.repository.SyncRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncHistoryService {

    private final SyncRunRepository syncRunRepository;

    public void save(SyncReport report) {
        if (report == null) {
            return;
        }

        try {
            SyncRun syncRun = SyncRun.builder()
                    .startedAt(report.getStartedAt())
                    .finishedAt(resolveFinishedAt(report))
                    .durationMs(resolveDurationMs(report))
                    .status(resolveStatus(report))

                    .googleSheetsReadSuccess(report.isGoogleSheetsReadSuccess())
                    .ymlFeedReady(report.isYmlFeedReady())
                    .ymlProductsCount(report.getYmlProductsCount())

                    .processedRows(report.getProcessedRows())
                    .skippedRows(report.getSkippedRows())
                    .unchangedProducts(report.getUnchangedProducts())

                    .newProducts(report.countChanges(SyncChangeType.NEW_PRODUCT))
                    .priceChanges(report.countChanges(SyncChangeType.PRICE_CHANGED))
                    .promPriceChanges(report.countChanges(SyncChangeType.PROM_PRICE_CHANGED))
                    .availabilityChanges(report.countChanges(SyncChangeType.AVAILABILITY_CHANGED))
                    .nameChanges(report.countChanges(SyncChangeType.NAME_CHANGED))
                    .missingFromDealer(report.countChanges(SyncChangeType.MISSING_FROM_DEALER))
                    .missingImages(report.countChanges(SyncChangeType.MISSING_IMAGE))

                    .errorsCount(report.getErrorCount())
                    .errorSummary(buildErrorSummary(report.getErrors()))
                    .changesSummary(buildChangesSummary(report.getChanges()))
                    .build();

            syncRunRepository.save(syncRun);

            log.info(
                    "Sync history saved. status={}, products={}, errors={}",
                    syncRun.getStatus(),
                    syncRun.getYmlProductsCount(),
                    syncRun.getErrorsCount()
            );

        } catch (Exception e) {
            log.error("Cannot save sync history", e);
        }
    }

    private String resolveStatus(SyncReport report) {
        if (!report.isGoogleSheetsReadSuccess() || !report.isYmlFeedReady()) {
            return "FAILED";
        }

        if (report.getErrorCount() > 0) {
            return "WARNING";
        }

        return "SUCCESS";
    }

    private LocalDateTime resolveFinishedAt(SyncReport report) {
        if (report.getFinishedAt() != null) {
            return report.getFinishedAt();
        }

        return LocalDateTime.now();
    }

    private Long resolveDurationMs(SyncReport report) {
        LocalDateTime startedAt = report.getStartedAt();
        LocalDateTime finishedAt = resolveFinishedAt(report);

        if (startedAt == null || finishedAt == null) {
            return null;
        }

        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private String buildErrorSummary(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }

        String summary = errors.stream()
                .limit(30)
                .collect(Collectors.joining("\n"));

        if (errors.size() > 30) {
            summary += "\n... і ще " + (errors.size() - 30) + " помилок";
        }

        return summary;
    }

    private String buildChangesSummary(List<SyncItemChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return "";
        }

        String summary = changes.stream()
                .limit(100)
                .map(this::formatChange)
                .collect(Collectors.joining("\n"));

        if (changes.size() > 100) {
            summary += "\n... і ще " + (changes.size() - 100) + " змін";
        }

        return summary;
    }

    private String formatChange(SyncItemChange change) {
        return change.type()
                + " | " + safe(change.sku())
                + " | " + safe(change.productName())
                + " | " + safe(change.oldValue())
                + " -> " + safe(change.newValue());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
