package com.els.promsync.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sync_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    private Long durationMs;

    @Column(nullable = false)
    private String status; // SUCCESS / WARNING / FAILED

    private boolean googleSheetsReadSuccess;
    private boolean ymlFeedReady;

    private long ymlProductsCount;

    private int processedRows;
    private int skippedRows;
    private int unchangedProducts;

    private long newProducts;
    private long priceChanges;
    private long promPriceChanges;
    private long availabilityChanges;
    private long nameChanges;
    private long missingFromDealer;
    private long missingImages;

    private int errorsCount;

    @Column(columnDefinition = "TEXT")
    private String errorSummary;

    @Column(columnDefinition = "TEXT")
    private String changesSummary;
}
