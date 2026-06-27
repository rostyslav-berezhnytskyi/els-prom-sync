package com.els.promsync;

import com.els.promsync.dto.SyncReport;
import com.els.promsync.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@RequiredArgsConstructor
public class ElsPromSyncApplication {

    private final ConfigurableApplicationContext applicationContext;

    @Value("${app.run-once:false}")
    private boolean runOnce;

    public static void main(String[] args) {
        SpringApplication.run(ElsPromSyncApplication.class, args);
    }

    @Bean
    CommandLineRunner run(
            GoogleSheetsService googleSheetsService,
            ProductMediaSyncService productMediaSyncService,
            PromFeedFileService promFeedFileService,
            ProductImageAuditService productImageAuditService,
            TelegramNotificationService telegramNotificationService,
            SyncHistoryService syncHistoryService
    ) {
        return args -> {
            System.out.println("--- Починаємо синхронізацію ELS Prom Sync ---");

            SyncReport report = googleSheetsService.testReadSheet();

            productMediaSyncService.syncImageUrlsFromFolders();

            promFeedFileService.writePromFeedFile(report);

            productImageAuditService.addMissingImagesToReport(report);

            report.finish();

            syncHistoryService.save(report);

            telegramNotificationService.sendSyncReport(report);

            System.out.println("--- Синхронізацію завершено ---");

            if (runOnce) {
                int exitCode = SpringApplication.exit(applicationContext, () -> 0);
                System.exit(exitCode);
            }
        };
    }
}