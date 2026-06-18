package com.els.promsync.dto;

public record SyncItemChange(
        SyncChangeType type,
        String sku,
        String productName,
        String category,
        String oldValue,
        String newValue
) {
}
