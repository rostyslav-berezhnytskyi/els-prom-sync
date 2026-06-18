package com.els.promsync.dto;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
public class SyncReport {

    private final LocalDateTime startedAt = LocalDateTime.now();
    private LocalDateTime finishedAt;

    private boolean googleSheetsReadSuccess;
    private boolean ymlFeedReady;
    private long ymlProductsCount;

    private int processedRows;
    private int skippedRows;
    private int unchangedProducts;

    private final List<SyncItemChange> changes = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    public void markGoogleSheetsReadSuccess() {
        this.googleSheetsReadSuccess = true;
    }

    public void markYmlFeedReady(long productsCount) {
        this.ymlFeedReady = true;
        this.ymlProductsCount = productsCount;
    }

    public void addProcessedRow() {
        processedRows++;
    }

    public void addSkippedRow() {
        skippedRows++;
    }

    public void addUnchangedProduct() {
        unchangedProducts++;
    }

    public void addChange(
            SyncChangeType type,
            String sku,
            String productName,
            String category,
            String oldValue,
            String newValue
    ) {
        changes.add(new SyncItemChange(type, sku, productName, category, oldValue, newValue));
    }

    public void addError(String message) {
        errors.add(message);
    }

    public long countChanges(SyncChangeType type) {
        return changes.stream()
                .filter(change -> change.type() == type)
                .count();
    }

    public int getErrorCount() {
        return errors.size();
    }

    public void finish() {
        this.finishedAt = LocalDateTime.now();
    }
}
